/* ==========================================================================
   backend-management 公共前端脚本
   约定：所有数据交互统一走 ajax（fetch），不使用表单原生提交。
   ========================================================================== */
(function (window, document) {
    'use strict';

    var CODE_SUCCESS = 0;
    var CODE_UNAUTHORIZED = 401;

    /**
     * 统一请求封装：自动处理 JSON 序列化、登录失效跳转与业务错误。
     * 后端返回 { code, message, data }，code === 0 视为成功，直接返回 data。
     */
    function request(url, options) {
        var opts = Object.assign({method: 'GET', headers: {}}, options || {});
        if (opts.body !== undefined && opts.body !== null && typeof opts.body !== 'string') {
            opts.headers['Content-Type'] = 'application/json;charset=UTF-8';
            opts.body = JSON.stringify(opts.body);
        }

        return window.fetch(url, opts).then(function (response) {
            if (response.status === CODE_UNAUTHORIZED) {
                window.location.href = '/login';
                throw new Error('未登录或登录已过期，请重新登录');
            }
            return response.text().then(function (text) {
                var payload = null;
                if (text) {
                    try {
                        payload = JSON.parse(text);
                    } catch (e) {
                        payload = null;
                    }
                }
                if (!payload) {
                    throw new Error('服务端返回内容无法解析（HTTP ' + response.status + '）');
                }
                if (payload.code !== CODE_SUCCESS) {
                    throw new Error(payload.message || '操作失败');
                }
                return payload.data;
            });
        });
    }

    /** 轻提示。type: ok / err / info */
    function toast(message, type) {
        var wrap = document.querySelector('.toast-wrap');
        if (!wrap) {
            wrap = document.createElement('div');
            wrap.className = 'toast-wrap';
            document.body.appendChild(wrap);
        }
        var item = document.createElement('div');
        item.className = 'toast' + (type === 'ok' ? ' toast--ok' : type === 'err' ? ' toast--err' : '');
        item.textContent = message;
        wrap.appendChild(item);
        window.setTimeout(function () {
            if (item.parentNode) {
                item.parentNode.removeChild(item);
            }
        }, type === 'err' ? 4200 : 2600);
    }

    /** 简易 HTML 转义，用于表格单元格拼接。 */
    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /** 空值占位。 */
    function dash(value) {
        return value === null || value === undefined || value === '' ? '—' : escapeHtml(value);
    }

    /** 还原常见 HTML 实体；&amp; 放最后，避免把 &amp;lt; 二次解码成 <。 */
    function decodeEntities(text) {
        return text
            .replace(/&lt;/g, '<')
            .replace(/&gt;/g, '>')
            .replace(/&quot;/g, '"')
            .replace(/&#0?39;/g, "'")
            .replace(/&apos;/g, "'")
            .replace(/&nbsp;/g, ' ')
            .replace(/&amp;/g, '&');
    }

    /**
     * 富文本转纯文本，供列表摘要这类只需文字的场景使用。
     *
     * <p>步骤：先整段丢弃 script / style，再把块级标签收尾换成换行，最后去掉其余标签并还原实体。
     * <p>注意：返回值**未经转义**，插入 DOM 前必须自己 escapeHtml，切勿直接 innerHTML——
     * 本函数的职责是「取文字」，不是「净化 HTML」。
     */
    function htmlToText(value) {
        if (value === null || value === undefined) {
            return '';
        }
        var text = String(value);
        if (text.indexOf('<') === -1) {
            return text;
        }
        text = text.replace(/<(script|style)\b[\s\S]*?<\/\1\s*>/gi, ' ');
        text = text.replace(/<br\s*\/?>/gi, '\n');
        text = text.replace(/<\/(p|div|li|h[1-6]|blockquote|tr|section|article)\s*>/gi, '\n');
        text = text.replace(/<[^>]*>/g, '');
        return decodeEntities(text)
            .replace(/[ \t]*\n[ \t]*/g, '\n')
            .replace(/\n{3,}/g, '\n\n')
            .trim();
    }

    /** yyyy-MM-dd HH:mm:ss -> 原样返回；ISO 字符串做一次美化。 */
    function formatDateTime(value) {
        if (!value) {
            return '';
        }
        return String(value).replace('T', ' ');
    }

    /** yyyyMMdd(整数) -> yyyy-MM-dd，供 date 控件使用。 */
    function toDateInputValue(compact) {
        if (!compact) {
            return '';
        }
        var text = String(compact);
        if (text.length !== 8) {
            return '';
        }
        return text.slice(0, 4) + '-' + text.slice(4, 6) + '-' + text.slice(6, 8);
    }

    /** yyyy-MM-dd -> yyyyMMdd(整数)，供接口提交使用。 */
    function toCompactDateValue(inputValue) {
        if (!inputValue) {
            return null;
        }
        var compact = String(inputValue).replace(/-/g, '');
        return compact.length === 8 ? parseInt(compact, 10) : null;
    }

    /* ------------------------------------------------ 日期时间（datetime-local） */

    /** yyyy-MM-dd HH:mm[:ss] -> yyyy-MM-ddTHH:mm，供 datetime-local 控件回填。 */
    function toDateTimeInputValue(text) {
        if (!text) {
            return '';
        }
        return String(text).replace(' ', 'T').slice(0, 16);
    }

    /** datetime-local 的值（yyyy-MM-ddTHH:mm）-> yyyy-MM-dd HH:mm:ss，供接口提交；空值返回 null。 */
    function fromDateTimeInputValue(value) {
        if (!value) {
            return null;
        }
        var text = String(value).replace('T', ' ');
        return text.length === 16 ? text + ':00' : text;
    }

    function pad2(value) {
        return value < 10 ? '0' + value : String(value);
    }

    /* ------------------------------------------------------ 日程提醒提前量 */

    /**
     * 提醒时间的可选提前量（分钟）。日程只存「提醒时刻」，提前量是它相对预约时间的差值，
     * 由前端换算：新建时按预设给出，编辑时按差值反解回预设。
     */
    var REMIND_PRESETS = [
        {minutes: 0, label: '开始时'},
        {minutes: 5, label: '前5分钟'},
        {minutes: 10, label: '前10分钟'},
        {minutes: 15, label: '前15分钟'},
        {minutes: 30, label: '前30分钟'}
    ];

    /** 新建日程的默认提前量（分钟）。 */
    var DEFAULT_REMIND_MINUTES = 15;

    /** Date -> yyyy-MM-dd HH:mm:ss（本地时区，与接口格式一致）。 */
    function formatDateTimeText(date) {
        return date.getFullYear() + '-' + pad2(date.getMonth() + 1) + '-' + pad2(date.getDate())
            + ' ' + pad2(date.getHours()) + ':' + pad2(date.getMinutes()) + ':' + pad2(date.getSeconds());
    }

    /** 在时间文本上加减分钟：yyyy-MM-dd HH:mm[:ss] -> yyyy-MM-dd HH:mm:ss；解析失败返回 null。 */
    function shiftMinutes(dateTimeText, deltaMinutes) {
        if (!dateTimeText) {
            return null;
        }
        var date = new Date(String(dateTimeText).replace(' ', 'T'));
        if (isNaN(date.getTime())) {
            return null;
        }
        date.setMinutes(date.getMinutes() + deltaMinutes);
        return formatDateTimeText(date);
    }

    /**
     * 反解提醒提前量（分钟）= 预约时间 - 提醒时间。
     *
     * <p>返回 null 表示无法比较（任一侧为空或格式非法），调用方据此判断「未设置提醒」；
     * 负数表示提醒时刻晚于预约时间（服务端会拒绝，前端只做展示）。
     */
    function remindOffsetMinutes(scheduleTime, remindTime) {
        if (!scheduleTime || !remindTime) {
            return null;
        }
        var at = new Date(String(scheduleTime).replace(' ', 'T'));
        var rt = new Date(String(remindTime).replace(' ', 'T'));
        if (isNaN(at.getTime()) || isNaN(rt.getTime())) {
            return null;
        }
        return Math.round((at.getTime() - rt.getTime()) / 60000);
    }

    /** 提前量 -> 预设文案；不在预设内（含 null）返回 null。 */
    function remindLabel(minutes) {
        for (var i = 0; i < REMIND_PRESETS.length; i++) {
            if (REMIND_PRESETS[i].minutes === minutes) {
                return REMIND_PRESETS[i].label;
            }
        }
        return null;
    }

    /** 今天，yyyy-MM-dd（本地时区）。 */
    function todayText(date) {
        var now = date || new Date();
        return now.getFullYear() + '-' + pad2(now.getMonth() + 1) + '-' + pad2(now.getDate());
    }

    /** 当前月份，yyyy-MM（本地时区）。 */
    function currentMonth(date) {
        var now = date || new Date();
        return now.getFullYear() + '-' + pad2(now.getMonth() + 1);
    }

    /** yyyy-MM 偏移若干月，用于月历翻页。 */
    function shiftMonth(month, offset) {
        var parts = String(month).split('-');
        var year = parseInt(parts[0], 10);
        var mon = parseInt(parts[1], 10) - 1 + offset;
        var date = new Date(year, mon, 1);
        return date.getFullYear() + '-' + pad2(date.getMonth() + 1);
    }

    /**
     * 渲染月历 HTML（周一起始，7 列网格）。
     *
     * @param options.month       显示月份 yyyy-MM
     * @param options.markedDates 需要打标点的日期数组 ['yyyy-MM-dd', ...]（同一天多个日程会有多个点）
     * @param options.today       今天 yyyy-MM-dd，用于高亮
     * @param options.selected    选中日期 yyyy-MM-dd，可空
     * @param options.maxDots     单元格最多显示几个点，超出显示 +n
     */
    function buildCalendar(options) {
        var opts = options || {};
        var month = opts.month || currentMonth();
        var parts = String(month).split('-');
        var year = parseInt(parts[0], 10);
        var mon = parseInt(parts[1], 10);
        var maxDots = opts.maxDots || 3;

        var counts = {};
        (opts.markedDates || []).forEach(function (date) {
            counts[date] = (counts[date] || 0) + 1;
        });

        var weekdays = ['一', '二', '三', '四', '五', '六', '日'];
        var html = '<div class="calendar__weekdays">';
        weekdays.forEach(function (name) {
            html += '<span class="calendar__weekday">' + name + '</span>';
        });
        html += '</div><div class="calendar__grid">';

        // 第一天是星期几：JS 的 0 是周日，换算成周一起始的下标
        var firstDay = new Date(year, mon - 1, 1).getDay();
        var offset = (firstDay + 6) % 7;
        for (var i = 0; i < offset; i++) {
            html += '<span class="calendar__cell calendar__cell--empty"></span>';
        }

        var total = new Date(year, mon, 0).getDate();
        for (var day = 1; day <= total; day++) {
            var dateText = year + '-' + pad2(mon) + '-' + pad2(day);
            var count = counts[dateText] || 0;

            var classes = ['calendar__cell'];
            if (dateText === opts.today) {
                classes.push('calendar__cell--today');
            }
            if (dateText === opts.selected) {
                classes.push('calendar__cell--selected');
            }
            if (count > 0) {
                classes.push('calendar__cell--marked');
            }

            var dots = '';
            for (var d = 0; d < Math.min(count, maxDots); d++) {
                dots += '<span class="calendar__dot"></span>';
            }
            if (count > maxDots) {
                dots += '<span class="calendar__more">+' + (count - maxDots) + '</span>';
            }

            html += '<button type="button" class="' + classes.join(' ') + '" data-date="' + dateText + '">'
                + '<span class="calendar__day">' + day + '</span>'
                + '<span class="calendar__dots">' + dots + '</span>'
                + '</button>';
        }
        html += '</div>';
        return html;
    }

    /** 轻量的空值判断（null / undefined / 空串）。 */
    function isBlank(value) {
        return value === null || value === undefined || String(value).trim() === '';
    }

    function debounce(fn, wait) {
        var timer = null;
        return function () {
            var args = arguments;
            var self = this;
            window.clearTimeout(timer);
            timer = window.setTimeout(function () {
                fn.apply(self, args);
            }, wait);
        };
    }

    /** 退出登录：全局按钮代理，任意页面可用。 */
    document.addEventListener('click', function (event) {
        var trigger = event.target.closest ? event.target.closest('[data-action="logout"]') : null;
        if (!trigger) {
            return;
        }
        request('/api/auth/logout', {method: 'POST'}).then(function () {
            window.location.href = '/login';
        }).catch(function () {
            window.location.href = '/login';
        });
    });

    /* ------------------------------------------------------ 顶部二级导航 */

    /**
     * 一级菜单（模块分组）的点击展开 / 收起。
     *
     * <p>CSS 已用 {@code :hover} / {@code :focus-within} 覆盖桌面悬停与键盘 Tab，
     * 这里补的是触屏：iOS Safari 点 {@code <button>} 不会给它焦点，只靠 CSS 打不开下拉。
     * 另外点了别处或按 Esc 必须收起，否则浮层会一直挂在页面上挡住内容。
     */
    (function bindNavMenus() {
        var items = Array.prototype.slice.call(document.querySelectorAll('.nav__item'));
        if (items.length === 0) {
            return;
        }

        function closeAll() {
            items.forEach(function (item) {
                item.classList.remove('is-open');
            });
        }

        items.forEach(function (item) {
            var trigger = item.querySelector('.nav__link--group');
            if (!trigger) {
                return;
            }
            trigger.addEventListener('click', function (event) {
                // 阻止冒泡到下面的 document 监听，否则刚打开就被自己关掉
                event.stopPropagation();
                var willOpen = !item.classList.contains('is-open');
                closeAll();
                if (willOpen) {
                    item.classList.add('is-open');
                }
            });
        });

        document.addEventListener('click', closeAll);
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closeAll();
            }
        });
    })();

    window.App = {
        request: request,
        get: function (url) {
            return request(url, {method: 'GET'});
        },
        post: function (url, body) {
            return request(url, {method: 'POST', body: body});
        },
        put: function (url, body) {
            return request(url, {method: 'PUT', body: body});
        },
        del: function (url) {
            return request(url, {method: 'DELETE'});
        },
        toast: toast,
        escapeHtml: escapeHtml,
        dash: dash,
        htmlToText: htmlToText,
        formatDateTime: formatDateTime,
        toDateInputValue: toDateInputValue,
        toCompactDateValue: toCompactDateValue,
        toDateTimeInputValue: toDateTimeInputValue,
        fromDateTimeInputValue: fromDateTimeInputValue,
        REMIND_PRESETS: REMIND_PRESETS,
        DEFAULT_REMIND_MINUTES: DEFAULT_REMIND_MINUTES,
        shiftMinutes: shiftMinutes,
        remindOffsetMinutes: remindOffsetMinutes,
        remindLabel: remindLabel,
        todayText: todayText,
        currentMonth: currentMonth,
        shiftMonth: shiftMonth,
        buildCalendar: buildCalendar,
        isBlank: isBlank,
        debounce: debounce
    };
})(window, document);
