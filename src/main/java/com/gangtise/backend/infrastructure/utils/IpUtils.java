package com.gangtise.backend.infrastructure.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * IP 工具：客户端 IP 提取 + 网段（CIDR）匹配。
 *
 * <p>两者对「代理透传头」的信任级别**刻意不同**，不要混用：
 * <ul>
 *   <li>{@link #getClientIp(HttpServletRequest)} 服务于**审计记录**，按「代理头优先、remoteAddr 兜底」解析。
 *       代理头由客户端可控，只作参考；</li>
 *   <li>{@link Network#contains(String)} 服务于**访问控制**（如打卡接口的来源白名单），
 *       入参只接受 {@code request.getRemoteAddr()}，**绝不吃任何请求头**——否则任何人加一个
 *       {@code X-Forwarded-For: 127.0.0.1} 就能冒充白名单来源，白名单等于没有。
 *       服务在 nginx 之后时，应通过 {@code server.forward-headers-strategy} 让容器改写
 *       remoteAddr（只信任直连代理），而不是在这里自己解析头。</li>
 * </ul>
 *
 * <p>网段解析没有走 {@code InetAddress.getByName}：它对非字面量入参会触发 DNS 解析，
 * 把一次访问判定变成一次网络调用（且给了伪造内容做外联的机会）。这里自己按字节解析，
 * 无网络副作用，也便于把 {@code ::ffff:127.0.0.1}、{@code fe80::1%en0} 这类真实会遇到的
 * 写法先归一再比较。
 */
public final class IpUtils {

    /** 常见反向代理透传头，按优先级依次尝试 */
    private static final String[] PROXY_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    private static final String UNKNOWN = "unknown";

    /** 严格 IPv4：四段点分十进制，每段 0~255 */
    private static final Pattern IPV4 = Pattern.compile(
            "^(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)){3}$");

    /**
     * 宽松 IPv6：必须含冒号，只允许十六进制字符、冒号与点（兼容 {@code ::ffff:192.168.1.1} 这类
     * IPv4 映射写法），长度不超过 IPv6 上限 45。审计字段不追求严格的段数校验。
     */
    private static final Pattern IPV6 = Pattern.compile("^(?=.*:)[0-9a-fA-F:.]{2,45}$");

    /** IPv4 与 IPv6 的地址字节数。 */
    private static final int IPV4_BYTES = 4;
    private static final int IPV6_BYTES = 16;

    private IpUtils() {
    }

    /* ------------------------------------------------------------------ 客户端 IP 提取 */

    /**
     * 解析客户端 IP，解析不到时返回 {@code null}。
     *
     * <p><b>仅供审计记录使用，不得用于鉴权判定</b>（原因见类注释）。
     *
     * @param request 当前请求，可为 null
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        for (String header : PROXY_HEADERS) {
            String candidate = firstAddress(request.getHeader(header));
            if (candidate != null) {
                return candidate;
            }
        }
        return parse(request.getRemoteAddr());
    }

    /**
     * 取代理头中的第一个地址：{@code X-Forwarded-For} 形如
     * {@code client, proxy1, proxy2}，第一个才是客户端地址。
     */
    private static String firstAddress(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        String value = headerValue.trim();
        if (UNKNOWN.equalsIgnoreCase(value)) {
            return null;
        }
        int comma = value.indexOf(',');
        if (comma > 0) {
            value = value.substring(0, comma).trim();
        }
        if (UNKNOWN.equalsIgnoreCase(value)) {
            return null;
        }
        return parse(value);
    }

    /** 必须是合法 IPv4 / IPv6 才采用，否则返回 null */
    private static String parse(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String ip = value.trim();
        if (IPV4.matcher(ip).matches() || IPV6.matcher(ip).matches()) {
            return ip;
        }
        return null;
    }

    /* ------------------------------------------------------------------ 网段匹配 */

    /**
     * 批量解析网段，元素允许写成一整个逗号串（环境变量覆盖后就是这个形态）。
     *
     * <p>任何一项非法都会抛 {@link IllegalArgumentException}——配置写错应当在**启动时**炸掉，
     * 而不是静默变成「什么都不匹配」让打卡悄悄全部失败，或者更糟：变成「什么都匹配」。
     *
     * @param specs 形如 {@code 127.0.0.1/32}、{@code 192.168.1.0/24}、{@code 10.0.0.0/8}、{@code ::1/128}
     */
    public static List<Network> parseNetworks(Collection<String> specs) {
        if (specs == null || specs.isEmpty()) {
            return List.of();
        }
        List<Network> networks = new ArrayList<>(specs.size());
        for (String spec : specs) {
            if (!StringUtils.hasText(spec)) {
                continue;
            }
            for (String part : spec.split(",")) {
                if (StringUtils.hasText(part)) {
                    networks.add(parseNetwork(part));
                }
            }
        }
        return List.copyOf(networks);
    }

    /**
     * 解析单个网段。不写 {@code /前缀} 时按「单个地址」处理，即补满前缀长度。
     *
     * @throws IllegalArgumentException 地址或前缀非法
     */
    public static Network parseNetwork(String spec) {
        String text = spec == null ? "" : spec.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("网段配置不能为空");
        }
        int slash = text.indexOf('/');
        String addressPart = slash < 0 ? text : text.substring(0, slash).trim();
        byte[] bytes = toBytes(addressPart);
        if (bytes == null) {
            throw new IllegalArgumentException("不是合法的 IP 或 CIDR：" + text);
        }

        int maxPrefix = bytes.length * 8;
        int prefixLength = maxPrefix;
        if (slash >= 0) {
            String prefixPart = text.substring(slash + 1).trim();
            try {
                prefixLength = Integer.parseInt(prefixPart);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("CIDR 前缀不是整数：" + text);
            }
            if (prefixLength < 0 || prefixLength > maxPrefix) {
                throw new IllegalArgumentException("CIDR 前缀应在 0~" + maxPrefix + " 之间：" + text);
            }
        }
        // 掩掉主机位：写成 192.168.1.7/24 时基址归一到 192.168.1.0。
        // 比较本身只看前 prefixLength 位，掩不掩结果都一样，但日志里能一眼看出实际放行范围
        return new Network(text, mask(bytes, prefixLength), prefixLength);
    }

    /**
     * 把 IP 字面量解析成地址字节，非法返回 {@code null}。
     *
     * <p>会先归一两件事：去掉 IPv6 的作用域标识（{@code fe80::1%en0}，{@code getRemoteAddr()}
     * 可能是这种形态），以及把 IPv4 映射地址 {@code ::ffff:127.0.0.1} 还原成 4 字节 IPv4
     * （双栈容器里连 127.0.0.1 也常以映射写法出现，不归一就会漏判）。
     */
    public static byte[] toBytes(String ipLiteral) {
        if (!StringUtils.hasText(ipLiteral)) {
            return null;
        }
        String ip = ipLiteral.trim();
        int percent = ip.indexOf('%');
        if (percent >= 0) {
            ip = ip.substring(0, percent);
        }
        return ip.indexOf(':') < 0 ? parseIpv4(ip) : parseIpv6(ip);
    }

    private static byte[] parseIpv4(String ip) {
        if (!IPV4.matcher(ip).matches()) {
            return null;
        }
        String[] parts = ip.split("\\.");
        byte[] bytes = new byte[IPV4_BYTES];
        for (int i = 0; i < IPV4_BYTES; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i]);
        }
        return bytes;
    }

    private static byte[] parseIpv6(String ip) {
        String text = ip;
        // 末尾内嵌 IPv4（::ffff:192.168.1.1）先折成两段十六进制，后面按统一形态处理
        int lastColon = text.lastIndexOf(':');
        if (lastColon < 0) {
            // 调用方已保证含冒号，这里只是兜底
            return null;
        }
        if (text.indexOf('.') >= 0) {
            byte[] embedded = parseIpv4(text.substring(lastColon + 1));
            if (embedded == null) {
                return null;
            }
            int high = (embedded[0] & 0xFF) << 8 | (embedded[1] & 0xFF);
            int low = (embedded[2] & 0xFF) << 8 | (embedded[3] & 0xFF);
            text = text.substring(0, lastColon + 1)
                    + Integer.toHexString(high) + ":" + Integer.toHexString(low);
        }

        String[] halves = text.split("::", -1);
        if (halves.length > 2) {
            return null;
        }
        List<Integer> head = parseGroups(halves[0]);
        List<Integer> tail = halves.length == 2 ? parseGroups(halves[1]) : List.of();
        if (head == null || tail == null) {
            return null;
        }

        List<Integer> groups = new ArrayList<>(8);
        if (halves.length == 2) {
            // "::" 至少压缩一组，否则前后段相加已经超 8 段
            int zeros = 8 - head.size() - tail.size();
            if (zeros < 1) {
                return null;
            }
            groups.addAll(head);
            for (int i = 0; i < zeros; i++) {
                groups.add(0);
            }
            groups.addAll(tail);
        } else {
            if (head.size() != 8) {
                return null;
            }
            groups.addAll(head);
        }

        byte[] bytes = new byte[IPV6_BYTES];
        for (int i = 0; i < 8; i++) {
            bytes[i * 2] = (byte) (groups.get(i) >> 8);
            bytes[i * 2 + 1] = (byte) (groups.get(i) & 0xFF);
        }
        return normalizeMapped(bytes);
    }

    /** 解析冒号分隔的十六进制段，非法返回 {@code null}（空串解析成空列表，供 {@code ::} 使用）。 */
    private static List<Integer> parseGroups(String part) {
        List<Integer> groups = new ArrayList<>(8);
        if (part.isEmpty()) {
            return groups;
        }
        for (String token : part.split(":", -1)) {
            if (token.isEmpty() || token.length() > 4) {
                return null;
            }
            int value = 0;
            for (int i = 0; i < token.length(); i++) {
                int digit = Character.digit(token.charAt(i), 16);
                if (digit < 0) {
                    return null;
                }
                value = value * 16 + digit;
            }
            groups.add(value);
        }
        return groups;
    }

    /** {@code ::ffff:a.b.c.d} 还原成 4 字节 IPv4；其余原样返回。 */
    private static byte[] normalizeMapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return bytes;
            }
        }
        if ((bytes[10] & 0xFF) != 0xFF || (bytes[11] & 0xFF) != 0xFF) {
            return bytes;
        }
        return new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
    }

    /** 保留前 {@code prefixLength} 位，其余置 0。 */
    private static byte[] mask(byte[] bytes, int prefixLength) {
        byte[] masked = bytes.clone();
        int fullBytes = prefixLength / 8;
        for (int i = fullBytes + (prefixLength % 8 == 0 ? 0 : 1); i < masked.length; i++) {
            masked[i] = 0;
        }
        int remainingBits = prefixLength % 8;
        if (remainingBits != 0) {
            masked[fullBytes] = (byte) (masked[fullBytes] & (0xFF << (8 - remainingBits)));
        }
        return masked;
    }

    /**
     * 一个已解析的网段。
     *
     * @param spec         原始配置文本，仅用于日志
     * @param base         网络号字节
     * @param prefixLength 前缀长度（0 表示放行该协议族全部地址）
     */
    public record Network(String spec, byte[] base, int prefixLength) {

        /** 判断地址是否落在本网段内。地址非法或协议族不同一律不匹配。 */
        public boolean contains(String ipLiteral) {
            byte[] candidate = toBytes(ipLiteral);
            if (candidate == null || candidate.length != base.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != base[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits) & 0xFF;
            return (candidate[fullBytes] & mask) == (base[fullBytes] & mask);
        }

        /** 前缀为 0，等于放行该协议族的全部地址，启动时应告警。 */
        public boolean allowsEverything() {
            return prefixLength == 0;
        }

        @Override
        public String toString() {
            return spec;
        }
    }

}
