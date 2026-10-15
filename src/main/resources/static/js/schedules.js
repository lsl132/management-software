/* ==========================================================================
   我的日程页脚本：月历 + 当月日程增删改查，全部 ajax
   约定：日程归属当前登录用户，用户ID由服务端从会话取，前端不传
   ========================================================================== */
(function (window, document) {
    'use strict';

    var App = window.App;

    var state = {
        /** 当前查看的月份 yyyy-MM */
        month: '',
        keyword: '',
        /** 选中的日期 yyyy-MM-dd，为空表示看整月 */
        selectedDate: '',
        /** 当月日程缓存 */
        schedules: [],
        editingId: null
    };

    var el = {
        month: document.getElementById('month'),
        keyword: document.getElementById('keyword'),
        btnSearch: document.getElementById('btnSearch'),
        btnReset: document.getElementById('btnReset'),
        btnCreate: document.getElementById('btnCreate'),
        calendar: document.getElementById('calendar'),
        body: document.getElementById('scheduleBody'),
        listTitle: document.getElementById('listTitle'),
        listSummary: document.getElementById('listSummary'),

        modal: document.getElementById('scheduleModal'),
        modalTitle: document.getElementById('scheduleModalTitle'),
        form: document.getElementById('scheduleForm'),
        error: document.getElementById('scheduleError'),
        scheduleTime: document.getElementById('scheduleTime'),
        remindOffset: document.getElementById('remindOffset'),
        remindPreview: document.getElementById('remindPreview'),
        title: document.getElementById('title'),
        intro: document.getElementById('intro'),
        btnSubmit: document.getElementById('btnSubmitSchedule')
    };

    /* ------------------------------------------------------------ 渲染 */

    /** 日期部分，yyyy-MM-dd。 */
    function dateOf(schedule) {
        return String(schedule.scheduleTime || '').slice(0, 10);
    }

    /** 时间部分，HH:mm。 */
    function timeOf(schedule) {
        var text = String(schedule.scheduleTime || '');
        return text.length >= 16 ? text.slice(11, 16) : text;
    }

    function visibleSchedules() {
        if (!state.selectedDate) {
            return state.schedules;
        }
        return state.schedules.filter(function (item) {
            return dateOf(item) === state.selectedDate;
        });
    }

    /** 提醒列：能对上预设就展示「前15分钟 + 时刻」，对不上只展示时刻。 */
    function remindCell(row) {
        if (!row.remindTime) {
            return '<span class="text-muted">不提醒</span>';
        }
        var label = App.remindLabel(App.remindOffsetMinutes(row.scheduleTime, row.remindTime));
        var time = App.formatDateTime(row.remindTime);
        return label
            ? App.escapeHtml(label) + ' <span class="text-muted">' + time + '</span>'
            : time;
    }

    function renderCalendar() {
        el.calendar.innerHTML = ''
            + '<div class="calendar__toolbar">'
            + '<button type="button" class="btn btn--sm" id="btnPrevMonth">上一月</button>'
            + '<span class="calendar__month">' + App.escapeHtml(state.month) + '</span>'
            + '<button type="button" class="btn btn--sm" id="btnNextMonth">下一月</button>'
            + '<span class="toolbar__spacer"></span>'
            + '<span class="dash-panel__hint">圆点表示当天有日程，点击日期可只看当天</span>'
            + '</div>'
            + App.buildCalendar({
                month: state.month,
                markedDates: state.schedules.map(dateOf),
                today: App.todayText(),
                selected: state.selectedDate,
                maxDots: 3
            });
    }

    function renderRows(records) {
        if (!records || records.length === 0) {
            el.body.innerHTML = '<tr><td colspan="7" class="table__empty">'
                + (state.selectedDate ? '这一天暂无日程安排' : '本月暂无日程安排') + '</td></tr>';
            return;
        }
        el.body.innerHTML = records.map(function (row) {
            return '<tr>'
                + '<td>' + App.dash(row.id) + '</td>'
                + '<td>' + App.dash(App.formatDateTime(row.scheduleTime)) + '</td>'
                + '<td>' + remindCell(row) + '</td>'
                + '<td>' + App.dash(row.title) + '</td>'
                + '<td>' + App.dash(row.intro) + '</td>'
                + '<td>' + App.dash(App.formatDateTime(row.createTime)) + '</td>'
                + '<td><div class="table__actions">'
                + '<button type="button" class="btn btn--sm" data-edit="' + row.id + '">编辑</button>'
                + '<button type="button" class="btn btn--sm btn--danger" data-remove="' + row.id + '">删除</button>'
                + '</div></td>'
                + '</tr>';
        }).join('');
    }

    function renderList() {
        var records = visibleSchedules();
        el.listTitle.textContent = state.selectedDate
            ? '当天日程（' + state.selectedDate + '）'
            : '本月日程（' + state.month + '）';
        renderRows(records);
        el.listSummary.textContent = state.selectedDate
            ? '共 ' + records.length + ' 条，点击月历中的已选日期可取消筛选'
            : '共 ' + records.length + ' 条';
    }

    /* ------------------------------------------------------------ 数据加载 */

    function loadMonth() {
        var params = new window.URLSearchParams();
        params.set('month', state.month);
        if (state.keyword) {
            params.set('keyword', state.keyword);
        }

        el.body.innerHTML = '<tr><td colspan="7" class="table__empty">正在加载…</td></tr>';
        return App.get('/api/schedules?' + params.toString()).then(function (list) {
            state.schedules = list || [];
            // 关键字或月份变化后，原本选中的日期可能已无数据，这里保留选中态以便用户查看空结果
            renderCalendar();
            renderList();
        }).catch(function (error) {
            el.body.innerHTML = '<tr><td colspan="7" class="table__empty">加载失败：'
                + App.escapeHtml(error.message) + '</td></tr>';
            App.toast(error.message, 'err');
        });
    }

    /* ------------------------------------------------------------ 弹窗 */

    /** 自定义档位的文案：负数说明提醒晚于预约时间（接口写入的异常数据）。 */
    function describeOffset(offset) {
        return offset >= 0 ? '前' + offset + '分钟' : '晚于预约' + (-offset) + '分钟';
    }

    /** 表单里选中的提醒提前量（分钟）；空字符串表示「不提醒」，非法值按不提醒兜底。 */
    function selectedRemindOffset() {
        var value = el.remindOffset.value;
        if (value === '') {
            return null;
        }
        var offset = Number(value);
        return isNaN(offset) ? null : offset;
    }

    /**
     * 由「预约时间 - 提前量」算出提醒时刻（提交给接口的值）。
     * 提前量为「不提醒」或预约时间为空时返回 null。
     */
    function computeRemindTime(scheduleTime) {
        var offset = selectedRemindOffset();
        if (offset === null || !scheduleTime) {
            return null;
        }
        return App.shiftMinutes(scheduleTime, -offset);
    }

    /**
     * 回填提醒下拉：预设 5 档，必要时追加兜底档。
     *
     * <p>日程只存提醒时刻，编辑时按「预约时间 - 提醒时间」反解提前量：
     * 落在预设内就直接选中；否则说明是接口写入或历史数据（也可能是未设置提醒），
     * 此时追加一档保住原值，避免编辑一次就把已有提醒悄悄改掉。
     */
    function fillRemindOptions(record, scheduleTimeText) {
        var presets = App.REMIND_PRESETS.map(function (preset) {
            return '<option value="' + preset.minutes + '">' + App.escapeHtml(preset.label) + '</option>';
        }).join('');

        if (!record) {
            el.remindOffset.innerHTML = presets;
            el.remindOffset.value = String(App.DEFAULT_REMIND_MINUTES);
            return;
        }

        if (!record.remindTime) {
            el.remindOffset.innerHTML = presets + '<option value="">不提醒</option>';
            el.remindOffset.value = '';
            return;
        }

        var offset = App.remindOffsetMinutes(scheduleTimeText, record.remindTime);
        if (offset === null || isNaN(offset)) {
            // 时间格式异常（正常接口不会出现）时退回「不提醒」，避免生成非法选项值
            el.remindOffset.innerHTML = presets + '<option value="">不提醒</option>';
            el.remindOffset.value = '';
            return;
        }
        if (App.remindLabel(offset) === null) {
            el.remindOffset.innerHTML = presets
                + '<option value="' + offset + '">自定义（' + describeOffset(offset) + '）</option>';
            el.remindOffset.value = String(offset);
            return;
        }

        el.remindOffset.innerHTML = presets;
        el.remindOffset.value = String(offset);
    }

    /** 实时预览换算结果，让「前端计算时间」这件事对用户可见。 */
    function renderRemindPreview() {
        var offset = selectedRemindOffset();
        if (offset === null) {
            el.remindPreview.textContent = '不会发送提醒';
            return;
        }
        var scheduleTime = App.fromDateTimeInputValue(el.scheduleTime.value);
        if (!scheduleTime) {
            el.remindPreview.textContent = '请先选择预约时间';
            return;
        }
        var remindTime = App.shiftMinutes(scheduleTime, -offset);
        if (!remindTime) {
            el.remindPreview.textContent = '时间格式无法识别，请重新选择预约时间';
            return;
        }
        var label = App.remindLabel(offset);
        el.remindPreview.textContent = (label ? label + '提醒' : '提前' + offset + '分钟提醒')
            + ' · ' + App.formatDateTime(remindTime);
    }

    function openModal(record) {
        state.editingId = record ? record.id : null;
        el.modalTitle.textContent = record ? '编辑日程' : '新建日程';
        el.error.textContent = '';

        el.scheduleTime.value = record ? App.toDateTimeInputValue(record.scheduleTime) : '';
        fillRemindOptions(record, App.fromDateTimeInputValue(el.scheduleTime.value));
        el.title.value = record ? record.title : '';
        el.intro.value = record && record.intro ? record.intro : '';

        renderRemindPreview();
        el.modal.hidden = false;
        el.title.focus();
    }

    function closeModal() {
        el.modal.hidden = true;
        state.editingId = null;
        el.error.textContent = '';
    }

    function submitForm(event) {
        event.preventDefault();

        var scheduleTime = App.fromDateTimeInputValue(el.scheduleTime.value);
        var remindTime = computeRemindTime(scheduleTime);
        var title = el.title.value.trim();

        if (!scheduleTime) {
            el.error.textContent = '请选择预约时间';
            return;
        }
        if (!title) {
            el.error.textContent = '请填写日程标题';
            return;
        }
        // 提前量非负时提醒必然不晚于预约；这里只兜住接口写入的异常提前量
        if (remindTime && remindTime > scheduleTime) {
            el.error.textContent = '提醒时间不能晚于预约时间';
            return;
        }

        var payload = {
            scheduleTime: scheduleTime,
            remindTime: remindTime,
            title: title,
            intro: el.intro.value.trim() || null
        };

        var editingId = state.editingId;
        el.error.textContent = '';
        el.btnSubmit.disabled = true;

        var pending = editingId
            ? App.put('/api/schedules/' + editingId, payload)
            : App.post('/api/schedules', payload);

        pending.then(function () {
            closeModal();
            App.toast(editingId ? '日程修改成功' : '日程创建成功', 'ok');
            // 新建的日程可能落在其他月份，跟随到它的月份，避免「保存后看不到」
            var monthOfNew = payload.scheduleTime.slice(0, 7);
            if (!editingId && monthOfNew !== state.month) {
                state.month = monthOfNew;
                el.month.value = monthOfNew;
                state.selectedDate = '';
            }
            loadMonth();
        }).catch(function (error) {
            el.error.textContent = error.message || '保存失败';
        }).then(function () {
            el.btnSubmit.disabled = false;
        });
    }

    function removeRecord(id, titleText) {
        if (!window.confirm('确认删除日程「' + titleText + '」吗？')) {
            return;
        }
        App.del('/api/schedules/' + id).then(function () {
            App.toast('日程删除成功', 'ok');
            loadMonth();
        }).catch(function (error) {
            App.toast(error.message || '删除失败', 'err');
        });
    }

    /* ------------------------------------------------------------ 事件 */

    function bindEvents() {
        el.btnSearch.addEventListener('click', function () {
            state.month = el.month.value || App.currentMonth();
            state.keyword = el.keyword.value.trim();
            state.selectedDate = '';
            loadMonth();
        });

        el.btnReset.addEventListener('click', function () {
            state.month = App.currentMonth();
            state.keyword = '';
            state.selectedDate = '';
            el.month.value = state.month;
            el.keyword.value = '';
            loadMonth();
        });

        el.keyword.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                el.btnSearch.click();
            }
        });

        el.btnCreate.addEventListener('click', function () {
            openModal(null);
        });

        // 月历：翻月 + 选中日期（按钮由 buildCalendar 动态生成，走事件代理）
        el.calendar.addEventListener('click', function (event) {
            var target = event.target;
            if (!target.closest) {
                return;
            }
            if (target.closest('#btnPrevMonth')) {
                state.month = App.shiftMonth(state.month, -1);
                el.month.value = state.month;
                state.selectedDate = '';
                loadMonth();
                return;
            }
            if (target.closest('#btnNextMonth')) {
                state.month = App.shiftMonth(state.month, 1);
                el.month.value = state.month;
                state.selectedDate = '';
                loadMonth();
                return;
            }
            var cell = target.closest('[data-date]');
            if (cell) {
                var date = cell.getAttribute('data-date');
                state.selectedDate = state.selectedDate === date ? '' : date;
                renderCalendar();
                renderList();
            }
        });

        el.body.addEventListener('click', function (event) {
            var target = event.target;
            if (!target.closest) {
                return;
            }

            var editBtn = target.closest('[data-edit]');
            if (editBtn) {
                var editId = editBtn.getAttribute('data-edit');
                App.get('/api/schedules/' + editId).then(function (record) {
                    openModal(record);
                }).catch(function (error) {
                    App.toast(error.message, 'err');
                });
                return;
            }

            var removeBtn = target.closest('[data-remove]');
            if (removeBtn) {
                var row = removeBtn.closest('tr');
                var titleCell = row ? row.children[3] : null;
                removeRecord(removeBtn.getAttribute('data-remove'),
                    titleCell ? titleCell.textContent : '该日程');
            }
        });

        el.form.addEventListener('submit', submitForm);

        // 预约时间或提前量变化时重算预览（提前量是「相对」的，改预约时间提醒要跟着走）
        el.scheduleTime.addEventListener('input', renderRemindPreview);
        el.scheduleTime.addEventListener('change', renderRemindPreview);
        el.remindOffset.addEventListener('change', renderRemindPreview);

        el.modal.addEventListener('click', function (event) {
            if (event.target.closest && event.target.closest('[data-close]')) {
                closeModal();
            }
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && !el.modal.hidden) {
                closeModal();
            }
        });
    }

    state.month = App.currentMonth();
    el.month.value = state.month;
    bindEvents();
    loadMonth();
})(window, document);
