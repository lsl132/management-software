package com.management.software.infrastructure.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网段匹配单测。
 *
 * <p>这是打卡接口唯一的门禁，判错的两个方向代价不对称：多放行一个地址 = 谁都能伪造打卡，
 * 误拒一个网段 = 整个办公室打不上卡。所以边界（前缀非整字节、掩码归一、协议族、
 * IPv4 映射写法）要逐个钉死。
 */
class IpNetworkTest {

    @Nested
    @DisplayName("IPv4 网段")
    class Ipv4 {

        @Test
        @DisplayName("/24 只放行同网段")
        void matchesWithinPrefix() {
            IpUtils.Network network = IpUtils.parseNetwork("192.168.1.0/24");
            assertTrue(network.contains("192.168.1.1"));
            assertTrue(network.contains("192.168.1.254"));
            assertFalse(network.contains("192.168.2.1"));
            assertFalse(network.contains("192.168.0.254"));
        }

        @Test
        @DisplayName("不写前缀时按单地址处理，与 /32 等价")
        void bareAddressIsSingleHost() {
            IpUtils.Network bare = IpUtils.parseNetwork("127.0.0.1");
            IpUtils.Network explicit = IpUtils.parseNetwork("127.0.0.1/32");

            assertTrue(bare.contains("127.0.0.1"));
            assertTrue(explicit.contains("127.0.0.1"));
            assertFalse(bare.contains("127.0.0.2"));
            assertFalse(explicit.contains("127.0.0.2"));
        }

        @Test
        @DisplayName("主机位被掩掉：192.168.1.7/24 仍覆盖整个 /24")
        void masksHostBits() {
            IpUtils.Network network = IpUtils.parseNetwork("192.168.1.7/24");
            assertArrayEquals(new byte[]{(byte) 0xC0, (byte) 0xA8, 0x01, 0x00}, network.base(),
                    "网络号应归一为 192.168.1.0");
            assertTrue(network.contains("192.168.1.99"), "掩码不应把放行范围缩成单个地址");
            assertFalse(network.contains("192.168.2.99"));
        }

        @Test
        @DisplayName("前缀落在字节中间时按位比较（/25 的第 128 号地址是分界）")
        void matchesOnNonByteBoundary() {
            IpUtils.Network network = IpUtils.parseNetwork("192.168.1.0/25");
            assertTrue(network.contains("192.168.1.0"));
            assertTrue(network.contains("192.168.1.127"));
            assertFalse(network.contains("192.168.1.128"));
            assertFalse(network.contains("192.168.1.255"));
        }

        @Test
        @DisplayName("/8 覆盖整个 A 类段")
        void matchesSlashEight() {
            IpUtils.Network network = IpUtils.parseNetwork("10.0.0.0/8");
            assertTrue(network.contains("10.0.0.1"));
            assertTrue(network.contains("10.255.255.254"));
            assertFalse(network.contains("11.0.0.1"));
        }

        @Test
        @DisplayName("/0 放行全部 IPv4，并被标记为「放行一切」")
        void slashZeroAllowsAllIpv4() {
            IpUtils.Network network = IpUtils.parseNetwork("0.0.0.0/0");
            assertTrue(network.allowsEverything(), "启动时要靠这个标记打告警");
            assertTrue(network.contains("1.2.3.4"));
            assertTrue(network.contains("255.255.255.255"));
            assertFalse(network.contains("::1"), "协议族不同不能匹配");
        }

    }

    @Nested
    @DisplayName("IPv6 与写法归一")
    class Ipv6 {

        @Test
        @DisplayName("::1 的各种写法都能匹配")
        void matchesLoopbackInAnyNotation() {
            IpUtils.Network network = IpUtils.parseNetwork("::1/128");
            assertTrue(network.contains("::1"));
            assertTrue(network.contains("0:0:0:0:0:0:0:1"));
            assertFalse(network.contains("0:0:0:0:0:0:0:2"));
        }

        @Test
        @DisplayName("IPv6 网段按前缀匹配")
        void matchesIpv6Prefix() {
            IpUtils.Network network = IpUtils.parseNetwork("2001:db8::/32");
            assertTrue(network.contains("2001:db8:1234::1"));
            assertTrue(network.contains("2001:0db8:ffff:ffff:ffff:ffff:ffff:ffff"));
            assertFalse(network.contains("2001:db9::1"));
        }

        @Test
        @DisplayName("IPv4 映射写法归一成 IPv4：::ffff:127.0.0.1 命中 127.0.0.1/32")
        void normalizesIpv4MappedAddress() {
            IpUtils.Network network = IpUtils.parseNetwork("127.0.0.1/32");
            assertTrue(network.contains("::ffff:127.0.0.1"), "双栈容器里连回环也常是这个写法");
            assertTrue(network.contains("::ffff:7f00:1"), "同一地址的十六进制写法也要命中");
            assertFalse(network.contains("::ffff:127.0.0.2"));
        }

        @Test
        @DisplayName("作用域标识被忽略：fe80::1%en0 命中 fe80::/10")
        void stripsScopeId() {
            IpUtils.Network network = IpUtils.parseNetwork("fe80::/10");
            assertTrue(network.contains("fe80::1%en0"));
            assertTrue(network.contains("fe80::1"));
        }

        @Test
        @DisplayName("回环白名单不会误放行 IPv6 全零网段")
        void doesNotTreatZeroAddressAsMapped() {
            IpUtils.Network network = IpUtils.parseNetwork("::/128");
            assertTrue(network.contains("::"));
            assertFalse(network.contains("::1"));
        }

    }

    @Nested
    @DisplayName("配置解析")
    class Parsing {

        @Test
        @DisplayName("逗号串会被拆开，空白与空项跳过")
        void parsesCommaSeparatedSpecs() {
            List<IpUtils.Network> networks =
                    IpUtils.parseNetworks(List.of("192.168.1.0/24, 127.0.0.1/32", " ", "::1/128"));
            assertEquals(3, networks.size());
            assertTrue(networks.get(0).contains("192.168.1.5"));
            assertTrue(networks.get(1).contains("127.0.0.1"));
            assertTrue(networks.get(2).contains("::1"));
        }

        @Test
        @DisplayName("空配置解析成空列表（调用方按「拒绝一切」处理）")
        void emptySpecsYieldEmptyList() {
            assertTrue(IpUtils.parseNetworks(null).isEmpty());
            assertTrue(IpUtils.parseNetworks(List.of()).isEmpty());
            assertTrue(IpUtils.parseNetworks(List.of("  ")).isEmpty());
        }

        @Test
        @DisplayName("非法配置直接抛异常，不允许静默变成「什么都不匹配」或「什么都匹配」")
        void rejectsInvalidSpecs() {
            for (String bad : Arrays.asList(
                    "", " ", "not-an-ip", "1.2.3", "256.1.1.1", "192.168.1.0/33", "192.168.1.0/-1",
                    "192.168.1.0/abc", "1:2:3:4:5:6:7:8:9", "::1::2", ":::1", "2001:db8::/129")) {
                assertThrows(IllegalArgumentException.class, () -> IpUtils.parseNetwork(bad),
                        "应拒绝非法网段：" + bad);
            }
        }

        @Test
        @DisplayName("非法或空地址入参一律不匹配（不抛异常）")
        void invalidCandidateNeverMatches() {
            IpUtils.Network network = IpUtils.parseNetwork("192.168.1.0/24");
            assertFalse(network.contains(null));
            assertFalse(network.contains(""));
            assertFalse(network.contains("not-an-ip"));
            assertFalse(network.contains("192.168.1"));
            assertFalse(network.contains("256.168.1.1"));
        }

    }

}
