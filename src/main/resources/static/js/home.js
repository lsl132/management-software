/* ==========================================================================
   个人工作台（欢迎页）脚本
   三块数据全部 ajax 拉取：
     1. 我的登录记录 —— 近 10 天折线图（SVG 自绘，不引入任何外链图表库）
     2. 我的日历     —— 带日程标点，点击日期查看当天安排
     3. 发布的公告   —— 近 5 条，发布时间倒序（单行摘要，与日历卡片等高）
   ========================================================================== */
(function (window, document) {
    'use strict';

    var App = window.App;

    var TREND_DAYS = 10;
    var ANNOUNCEMENT_LIMIT = 5;

    var CHART_HEIGHT = 200;
    var COLOR_LINE = '#2f6fed';
    var COLOR_MUTED = '#6b7686';
    var COLOR_GRID = '#e4e8f0';
    var COLOR_AREA = 'rgba(47, 111, 237, 0.10)';

    var state = {
        month: '',
        selectedDate: '',
        schedules: [],
        /** 缓存趋势数据，窗口尺寸变化时重绘用 */
        trend: null
    };

    var el = {
        lastLoginTime: document.getElementById('lastLoginTime'),
        lastLoginIp: document.getElementById('lastLoginIp'),
        trendHint: document.getElementById('trendHint'),
        loginTrend: document.getElementById('loginTrend'),
        trendSummary: document.getElementById('trendSummary'),
        calendar: document.getElementById('calendar'),
        dayTitle: document.getElementById('dayTitle'),
        dayList: document.getElementById('dayList'),
        announcementList: document.getElementById('announcementList')
    };

    /* ------------------------------------------------------ 登录记录折线图 */

    /** 自绘 SVG 折线图：横轴为日期、纵轴为当天登录次数（min 0 / max 取区间峰值）。 */
    function renderTrendChart(trend) {
        var width = Math.max(320, el.loginTrend.clientWidth || 640);
        var pad = {top: 18, right: 18, bottom: 28, left: 32};
        var innerWidth = width - pad.left - pad.right;
        var innerHeight = CHART_HEIGHT - pad.top - pad.bottom;
        var points = trend && trend.points ? trend.points : [];
        var max = Math.max(1, Number(trend && trend.max) || 1);
        var lastIndex = points.length - 1;
        var step = lastIndex > 0 ? innerWidth / lastIndex : 0;

        var coords = points.map(function (point, index) {
            var count = Number(point.count) || 0;
            return {
                x: pad.left + step * index,
                y: pad.top + innerHeight * (1 - count / max),
                date: point.date,
                label: point.label,
                count: count
            };
        });

        var svg = '<svg viewBox="0 0 ' + width + ' ' + CHART_HEIGHT + '" width="' + width + '" height="'
            + CHART_HEIGHT + '" role="img" aria-label="近 ' + points.length + ' 天登录次数折线图">';

        // 横向网格线 + 纵轴刻度
        [0, 0.5, 1].forEach(function (ratio) {
            var y = pad.top + innerHeight * (1 - ratio);
            svg += '<line x1="' + pad.left + '" y1="' + y + '" x2="' + (width - pad.right) + '" y2="' + y
                + '" stroke="' + COLOR_GRID + '" stroke-width="1"/>';
            svg += '<text x="' + (pad.left - 8) + '" y="' + (y + 4) + '" text-anchor="end" font-size="11" fill="'
                + COLOR_MUTED + '">' + Math.round(max * ratio) + '</text>';
        });

        if (coords.length > 0) {
            var baseline = pad.top + innerHeight;
            var polyline = coords.map(function (item) {
                return item.x + ',' + item.y;
            }).join(' ');
            svg += '<polygon points="' + pad.left + ',' + baseline + ' ' + polyline + ' '
                + coords[coords.length - 1].x + ',' + baseline + '" fill="' + COLOR_AREA + '"/>';
            svg += '<polyline points="' + polyline + '" fill="none" stroke="' + COLOR_LINE
                + '" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>';
        }

        coords.forEach(function (item) {
            svg += '<circle cx="' + item.x + '" cy="' + item.y + '" r="3.5" fill="#ffffff" stroke="' + COLOR_LINE
                + '" stroke-width="2"><title>' + item.date + '：' + item.count + ' 次登录</title></circle>';
        });

        // 横轴标签：点多时隔一个显示，避免拥挤
        var labelStep = coords.length > 12 ? 2 : 1;
        coords.forEach(function (item, index) {
            if (index % labelStep !== 0 && index !== lastIndex) {
                return;
            }
            svg += '<text x="' + item.x + '" y="' + (CHART_HEIGHT - 8) + '" text-anchor="middle" font-size="11" fill="'
                + COLOR_MUTED + '">' + item.label + '</text>';
        });

        svg += '</svg>';
        el.loginTrend.innerHTML = svg;
    }

    function loadTrend() {
        return App.get('/api/dashboard/login-trend?days=' + TREND_DAYS).then(function (trend) {
            state.trend = trend;
            el.trendHint.textContent = '近 ' + trend.days + ' 天 · 共 ' + trend.total + ' 次';
            renderTrendChart(trend);
            el.trendSummary.textContent = trend.total > 0
                ? '近 ' + trend.days + ' 天共登录 ' + trend.total + ' 次，单日最多 ' + trend.max + ' 次。'
                : '近 ' + trend.days + ' 天还没有登录记录。';
            el.lastLoginTime.textContent = trend.lastLoginTime
                ? App.formatDateTime(trend.lastLoginTime) : '—';
            el.lastLoginIp.textContent = trend.lastLoginIp || '—';
        }).catch(function (error) {
            el.loginTrend.innerHTML = '<p class="empty-hint">登录记录加载失败：'
                + App.escapeHtml(error.message) + '</p>';
        });
    }

    /* ------------------------------------------------------------ 日历 */

    function dateOf(schedule) {
        return String(schedule.scheduleTime || '').slice(0, 10);
    }

    function timeOf(schedule) {
        var text = String(schedule.scheduleTime || '');
        return text.length >= 16 ? text.slice(11, 16) : text;
    }

    function renderCalendar() {
        el.calendar.innerHTML = ''
            + '<div class="calendar__toolbar">'
            + '<button type="button" class="btn btn--sm" id="btnPrevMonth">上一月</button>'
            + '<span class="calendar__month">' + App.escapeHtml(state.month) + '</span>'
            + '<button type="button" class="btn btn--sm" id="btnNextMonth">下一月</button>'
            + '<span class="toolbar__spacer"></span>'
            + '<button type="button" class="btn btn--sm" id="btnToday">回到本月</button>'
            + '</div>'
            + App.buildCalendar({
                month: state.month,
                markedDates: state.schedules.map(dateOf),
                today: App.todayText(),
                selected: state.selectedDate,
                maxDots: 3
            });
    }

    function renderDayList() {
        if (!state.selectedDate) {
            el.dayTitle.textContent = '本月安排';
            if (state.schedules.length === 0) {
                el.dayList.innerHTML = '<p class="empty-hint">本月暂无日程安排</p>';
                return;
            }
            el.dayList.innerHTML = state.schedules.map(function (item) {
                return dayRow(item, true);
            }).join('');
            return;
        }

        var records = state.schedules.filter(function (item) {
            return dateOf(item) === state.selectedDate;
        });
        el.dayTitle.textContent = state.selectedDate + ' 的安排';
        if (records.length === 0) {
            el.dayList.innerHTML = '<p class="empty-hint">这一天没有日程安排</p>';
            return;
        }
        el.dayList.innerHTML = records.map(function (item) {
            return dayRow(item, false);
        }).join('');
    }

    function dayRow(item, withDate) {
        var remind = '';
        if (item.remindTime) {
            // 能对上预设就写「前15分钟提醒」，否则退回具体时刻，避免展示错误语义
            var label = App.remindLabel(App.remindOffsetMinutes(item.scheduleTime, item.remindTime));
            remind = '<span class="day-schedule__intro">'
                + (label ? App.escapeHtml(label) + '提醒' : '提醒 ' + App.formatDateTime(item.remindTime).slice(5, 16))
                + '</span>';
        }
        var intro = item.intro
            ? '<span class="day-schedule__intro">' + App.escapeHtml(item.intro) + '</span>'
            : '';
        return '<div class="day-schedule">'
            + '<span class="day-schedule__time">'
            + (withDate ? App.escapeHtml(dateOf(item).slice(5)) + ' ' : '') + App.escapeHtml(timeOf(item))
            + '</span>'
            + '<span class="day-schedule__title">' + App.dash(item.title) + '</span>'
            + intro
            + remind
            + '</div>';
    }

    function loadCalendar() {
        return App.get('/api/schedules?month=' + encodeURIComponent(state.month)).then(function (list) {
            state.schedules = list || [];
            // 默认选中今天（仅当查看的是本月），让「今天的安排」开箱即见
            if (!state.selectedDate && state.month === App.currentMonth()) {
                state.selectedDate = App.todayText();
            }
            renderCalendar();
            renderDayList();
        }).catch(function (error) {
            el.calendar.innerHTML = '<p class="empty-hint">日历加载失败：'
                + App.escapeHtml(error.message) + '</p>';
        });
    }

    function switchMonth(offset) {
        state.month = App.shiftMonth(state.month, offset);
        state.selectedDate = '';
        loadCalendar();
    }

    /* ------------------------------------------------------------ 公告 */

    function renderAnnouncements(list) {
        if (!list || list.length === 0) {
            el.announcementList.innerHTML = '<li class="empty-hint">暂无已发布的公告</li>';
            return;
        }
        el.announcementList.innerHTML = list.map(function (item) {
            var summary = item.content ? String(item.content) : '';
            if (summary.length > 48) {
                summary = summary.slice(0, 48) + '…';
            }
            var attachment = item.attachment ? ' · 含附件' : '';
            return '<li class="ann-list__item">'
                + '<span class="ann-list__title">' + App.dash(item.title) + '</span>'
                + (summary ? '<span class="ann-list__meta ann-list__meta--summary">' + App.escapeHtml(summary) + '</span>' : '')
                + '<span class="ann-list__meta">' + App.dash(item.author) + ' · 发布于 '
                + App.dash(App.formatDateTime(item.publishTime)) + attachment + '</span>'
                + '</li>';
        }).join('');
    }

    function loadAnnouncements() {
        return App.get('/api/announcements/recent?limit=' + ANNOUNCEMENT_LIMIT).then(function (list) {
            renderAnnouncements(list);
        }).catch(function (error) {
            el.announcementList.innerHTML = '<li class="empty-hint">公告加载失败：'
                + App.escapeHtml(error.message) + '</li>';
        });
    }

    /* ------------------------------------------------------------ 事件 */

    el.calendar.addEventListener('click', function (event) {
        var target = event.target;
        if (!target.closest) {
            return;
        }
        if (target.closest('#btnPrevMonth')) {
            switchMonth(-1);
            return;
        }
        if (target.closest('#btnNextMonth')) {
            switchMonth(1);
            return;
        }
        if (target.closest('#btnToday')) {
            state.month = App.currentMonth();
            state.selectedDate = '';
            loadCalendar();
            return;
        }
        var cell = target.closest('[data-date]');
        if (cell) {
            var date = cell.getAttribute('data-date');
            state.selectedDate = state.selectedDate === date ? '' : date;
            renderCalendar();
            renderDayList();
        }
    });

    // 容器宽度变化时按缓存数据重绘折线图，避免拉伸变形
    var resizeTimer = null;
    window.addEventListener('resize', function () {
        window.clearTimeout(resizeTimer);
        resizeTimer = window.setTimeout(function () {
            if (state.trend) {
                renderTrendChart(state.trend);
            }
        }, 150);
    });

    state.month = App.currentMonth();
    loadTrend();
    loadCalendar();
    loadAnnouncements();
})(window, document);
