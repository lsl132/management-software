/* ==========================================================================
   员工管理页脚本：列表查询 + 新增 / 修改 / 删除，全部 ajax，无整页刷新
   ========================================================================== */
(function (window, document) {
    'use strict';

    var App = window.App;

    var state = {
        current: 1,
        size: 10,
        keyword: '',
        department: '',
        status: '',
        total: 0,
        loadedCount: 0,
        editingId: null,
        salaryEmployeeId: null
    };

    /** 薪酬换算基准：年薪按 12 个月，日薪按月计薪天数 21.75（法定标准）折算。 */
    var MONTHS_PER_YEAR = 12;
    var MONTH_WORKING_DAYS = 21.75;

    var el = {
        body: document.getElementById('employeeBody'),
        keyword: document.getElementById('keyword'),
        filterDepartment: document.getElementById('filterDepartment'),
        filterStatus: document.getElementById('filterStatus'),
        btnSearch: document.getElementById('btnSearch'),
        btnReset: document.getElementById('btnReset'),
        btnCreate: document.getElementById('btnCreate'),
        btnPrev: document.getElementById('btnPrev'),
        btnNext: document.getElementById('btnNext'),
        pageSize: document.getElementById('pageSize'),
        pagerInfo: document.getElementById('pagerInfo'),
        pageNow: document.getElementById('pageNow'),
        modal: document.getElementById('employeeModal'),
        modalTitle: document.getElementById('modalTitle'),
        form: document.getElementById('employeeForm'),
        formError: document.getElementById('formError'),
        btnSubmit: document.getElementById('btnSubmit'),
        department: document.getElementById('department'),
        rank: document.getElementById('rank'),
        salaryModal: document.getElementById('salaryModal'),
        salaryModalTitle: document.getElementById('salaryModalTitle'),
        salaryForm: document.getElementById('salaryForm'),
        salaryFormError: document.getElementById('salaryFormError'),
        salaryAudit: document.getElementById('salaryAudit'),
        salaryCreateTime: document.getElementById('salaryCreateTime'),
        salaryCurrency: document.getElementById('salaryCurrency'),
        salaryMonth: document.getElementById('salaryMonth'),
        salaryYear: document.getElementById('salaryYear'),
        salaryDay: document.getElementById('salaryDay'),
        btnSalarySubmit: document.getElementById('btnSalarySubmit')
    };

    /* ------------------------------------------------------------ 列表渲染 */

    function statusBadge(record) {
        var cls = 'badge--info';
        if (record.status === 1) {
            cls = 'badge--ok';
        } else if (record.status === 0) {
            cls = 'badge--warn';
        } else if (record.status === 2 || record.status === 3) {
            cls = 'badge--danger';
        }
        return '<span class="badge ' + cls + '">' + App.dash(record.statusText) + '</span>';
    }

    function renderRows(records) {
        if (!records || records.length === 0) {
            el.body.innerHTML = '<tr><td colspan="11" class="table__empty">暂无数据</td></tr>';
            return;
        }
        el.body.innerHTML = records.map(function (row) {
            return '<tr>'
                + '<td>' + App.dash(row.workNo) + '</td>'
                + '<td>' + App.dash(row.name) + '</td>'
                + '<td>' + App.dash(row.genderText) + '</td>'
                + '<td>' + App.dash(row.telephone) + '</td>'
                + '<td>' + App.dash(row.email) + '</td>'
                + '<td>' + App.dash(row.departmentName) + '</td>'
                + '<td>' + App.dash(row.rankName) + '</td>'
                + '<td>' + App.dash(row.educationText) + '</td>'
                + '<td>' + statusBadge(row) + '</td>'
                + '<td>' + App.dash(App.formatDateTime(row.updateTime)) + '</td>'
                + '<td><div class="table__actions">'
                + '<button type="button" class="btn btn--sm" data-edit="' + row.id + '">编辑</button>'
                + '<button type="button" class="btn btn--sm" data-salary="' + row.id + '"'
                + ' data-work-no="' + App.escapeHtml(row.workNo) + '"'
                + ' data-name="' + App.escapeHtml(row.name) + '">薪酬</button>'
                + '<button type="button" class="btn btn--sm btn--danger" data-remove="' + row.id + '">删除</button>'
                + '</div></td>'
                + '</tr>';
        }).join('');
    }

    function renderPager() {
        var pages = Math.max(1, Math.ceil(state.total / state.size));
        el.pagerInfo.textContent = '共 ' + state.total + ' 条';
        el.pageNow.textContent = state.current + ' / ' + pages;
        el.btnPrev.disabled = state.current <= 1;
        el.btnNext.disabled = state.current >= pages;
    }

    /* -------------------------------------------------------------- 数据加载 */

    function loadList() {
        var params = new window.URLSearchParams();
        params.set('current', state.current);
        params.set('size', state.size);
        if (state.keyword) {
            params.set('keyword', state.keyword);
        }
        if (state.department) {
            params.set('department', state.department);
        }
        if (state.status) {
            params.set('status', state.status);
        }

        el.body.innerHTML = '<tr><td colspan="11" class="table__empty">正在加载…</td></tr>';
        App.get('/api/employees?' + params.toString()).then(function (data) {
            // 分页计数一律显式转成数字：报文里若是字符串（全局 Long -> String 策略的连带影响），
            // state.current + 1 会变成字符串拼接（"1" + 1 === "11"），点「下一页」会直接跳到第 11 页。
            // 上一页用的是减号（"2" - 1 === 1），不会暴露这个问题——只有下一页会坏
            state.total = Number(data.total);
            state.current = Number(data.current);
            state.size = Number(data.size);
            state.loadedCount = data.records ? data.records.length : 0;
            renderRows(data.records);
            renderPager();
        }).catch(function (error) {
            el.body.innerHTML = '<tr><td colspan="11" class="table__empty">加载失败：'
                + App.escapeHtml(error.message) + '</td></tr>';
            App.toast(error.message, 'err');
        });
    }

    /* ---------------------------------------------------------------- 弹窗 */

    function fieldValue(id) {
        var node = document.getElementById(id);
        return node ? String(node.value).trim() : '';
    }

    function setField(id, value) {
        var node = document.getElementById(id);
        if (node) {
            node.value = value === null || value === undefined ? '' : String(value);
        }
    }

    /* ------------------------------------------------- 部门 → 职级 级联加载 */

    /**
     * 按部门加载职级选项：职级归属部门，未选部门时下拉禁用。
     *
     * @param departmentId 部门 id，空表示尚未选择
     * @param selectedRank 需要回显的职级 id（编辑时使用）
     * @param done         选项渲染完成后的回调
     */
    function loadRanks(departmentId, selectedRank, done) {
        var notify = done || function () {};

        if (!departmentId) {
            el.rank.innerHTML = '<option value="">请先选择所属部门</option>';
            el.rank.disabled = true;
            notify();
            return;
        }

        el.rank.disabled = true;
        el.rank.innerHTML = '<option value="">加载中…</option>';
        App.get('/api/options/ranks?department=' + encodeURIComponent(departmentId)).then(function (list) {
            var html = '<option value="">未选择</option>';
            (list || []).forEach(function (item) {
                html += '<option value="' + item.value + '">' + App.escapeHtml(item.label) + '</option>';
            });
            el.rank.innerHTML = html;
            el.rank.disabled = false;
            if (selectedRank !== null && selectedRank !== undefined) {
                el.rank.value = String(selectedRank);
            }
            notify();
        }).catch(function (error) {
            el.rank.innerHTML = '<option value="">职级加载失败</option>';
            App.toast(error.message || '职级加载失败', 'err');
        });
    }

    function openModal(record) {
        state.editingId = record ? record.id : null;
        el.modalTitle.textContent = record ? '修改员工' : '新增员工';
        el.formError.textContent = '';

        setField('workNo', record ? record.workNo : '');
        setField('name', record ? record.name : '');
        setField('alias', record ? record.alias : '');
        setField('gender', record && record.gender !== null && record.gender !== undefined ? record.gender : '1');
        setField('identityCard', record ? record.identityCard : '');
        setField('telephone', record ? record.telephone : '');
        setField('email', record ? record.email : '');
        setField('address', record ? record.address : '');
        setField('education', record && record.education !== null && record.education !== undefined ? record.education : '');
        setField('department', record && record.department !== null && record.department !== undefined ? record.department : '');
        setField('status', record && record.status !== null && record.status !== undefined ? record.status : '0');
        setField('birthday', record ? App.toDateInputValue(record.birthday) : '');

        // 先落部门，再按部门拉取职级选项并回显当前职级
        var rank = record && record.rank !== null && record.rank !== undefined ? record.rank : null;
        loadRanks(fieldValue('department'), rank);

        el.modal.hidden = false;
        document.getElementById('workNo').focus();
    }

    function closeModal() {
        el.modal.hidden = true;
        state.editingId = null;
        el.formError.textContent = '';
    }

    function showFormError(message) {
        el.formError.textContent = message || '';
    }

    /* ------------------------------------------------------------- 薪酬弹窗 */

    /** 金额取两位小数。直接乘除会留出 459.77000000000004 这类浮点尾巴。 */
    function round2(value) {
        return Math.round((value + Number.EPSILON) * 100) / 100;
    }

    /**
     * 月薪是换算基准：改月薪即刷新年薪（×12）与日薪（÷21.75）。
     *
     * <p>这两个值刷出来后仍可手工修改（表单上的说明写明了这件事）——现实中年薪常含年终奖，
     * 不等于月薪 × 12，所以不做「三者必须自洽」的校验。
     */
    function syncDerivedAmounts() {
        var raw = el.salaryMonth.value;
        var month = Number(raw);
        if (raw === '' || isNaN(month) || month < 0) {
            return;
        }
        el.salaryYear.value = round2(month * MONTHS_PER_YEAR).toFixed(2);
        el.salaryDay.value = round2(month / MONTH_WORKING_DAYS).toFixed(2);
    }

    function showSalaryError(message) {
        el.salaryFormError.textContent = message || '';
    }

    function renderSalaryAudit(record) {
        if (!record) {
            el.salaryAudit.textContent = '尚未设置过薪酬，保存后即生效。';
            el.salaryCreateTime.textContent = '尚未设置';
            return;
        }
        el.salaryAudit.textContent = '最后修改：' + App.dash(App.formatDateTime(record.updateTime))
            + (record.updateBy ? ' · ' + record.updateBy : '');
        el.salaryCreateTime.textContent = App.dash(App.formatDateTime(record.createTime));
    }

    /**
     * 打开薪酬弹窗。
     *
     * @param employeeId 员工主键
     * @param workNo     工号，仅用于标题回显
     * @param name       姓名，仅用于标题回显
     */
    function openSalaryModal(employeeId, workNo, name) {
        state.salaryEmployeeId = employeeId;
        el.salaryModalTitle.textContent = '员工薪酬 ·' + (name ? ' ' + name : '') + (workNo ? '（' + workNo + '）' : '');
        showSalaryError('');
        el.salaryAudit.textContent = '加载中…';
        el.salaryCreateTime.textContent = '';
        el.salaryCurrency.value = 'CNY';
        el.salaryMonth.value = '';
        el.salaryYear.value = '';
        el.salaryDay.value = '';

        el.salaryModal.hidden = false;

        App.get('/api/employees/' + employeeId + '/salary').then(function (record) {
            if (record) {
                el.salaryCurrency.value = record.currency || 'CNY';
                el.salaryMonth.value = Number(record.monthSalary).toFixed(2);
                el.salaryYear.value = Number(record.yearSalary).toFixed(2);
                el.salaryDay.value = Number(record.daySalary).toFixed(2);
            } else {
                // 尚未设置：预填 0 而不是留空，保存时不必再猜「空」是 0 还是没填
                el.salaryMonth.value = '0.00';
                el.salaryYear.value = '0.00';
                el.salaryDay.value = '0.00';
            }
            renderSalaryAudit(record);
            el.salaryMonth.focus();
        }).catch(function (error) {
            renderSalaryAudit(null);
            showSalaryError(error.message || '薪酬加载失败');
        });
    }

    function closeSalaryModal() {
        el.salaryModal.hidden = true;
        state.salaryEmployeeId = null;
        showSalaryError('');
    }

    /** 读取金额输入：空按 0 处理（0 表示该项不适用），非数字或负数给友好提示。 */
    function amountOf(id, label) {
        var raw = String(document.getElementById(id).value).trim();
        if (raw === '') {
            return 0;
        }
        var value = Number(raw);
        if (isNaN(value) || !isFinite(value)) {
            throw new Error('「' + label + '」请填写数字');
        }
        if (value < 0) {
            throw new Error('「' + label + '」不能为负数');
        }
        return value;
    }

    function submitSalary(event) {
        event.preventDefault();

        var employeeId = state.salaryEmployeeId;
        if (!employeeId) {
            return;
        }

        var payload;
        try {
            payload = {
                currency: el.salaryCurrency.value,
                monthSalary: amountOf('salaryMonth', '月薪'),
                yearSalary: amountOf('salaryYear', '年薪'),
                daySalary: amountOf('salaryDay', '日薪')
            };
        } catch (error) {
            showSalaryError(error.message);
            return;
        }

        showSalaryError('');
        el.btnSalarySubmit.disabled = true;
        App.put('/api/employees/' + employeeId + '/salary', payload).then(function () {
            closeSalaryModal();
            App.toast('薪酬已保存', 'ok');
        }).catch(function (error) {
            showSalaryError(error.message || '保存失败');
        }).then(function () {
            el.btnSalarySubmit.disabled = false;
        });
    }

    /* ---------------------------------------------------------------- 提交 */

    function numberOrNull(id) {
        var raw = fieldValue(id);
        return raw === '' ? null : Number(raw);
    }

    function collect() {
        return {
            workNo: fieldValue('workNo'),
            name: fieldValue('name'),
            alias: fieldValue('alias') || null,
            gender: numberOrNull('gender'),
            identityCard: fieldValue('identityCard') || null,
            telephone: fieldValue('telephone') || null,
            email: fieldValue('email') || null,
            address: fieldValue('address') || null,
            status: numberOrNull('status'),
            birthday: App.toCompactDateValue(fieldValue('birthday')),
            education: numberOrNull('education'),
            rank: numberOrNull('rank'),
            department: numberOrNull('department')
        };
    }

    function submitForm(event) {
        event.preventDefault();

        var payload = collect();
        if (!payload.workNo) {
            showFormError('请填写员工工号');
            return;
        }
        if (!payload.name) {
            showFormError('请填写员工姓名');
            return;
        }
        if (payload.rank && !payload.department) {
            showFormError('请先选择所属部门，再选择该部门下的职级');
            return;
        }

        var editingId = state.editingId;
        showFormError('');
        el.btnSubmit.disabled = true;

        var pending = editingId
            ? App.put('/api/employees/' + editingId, payload)
            : App.post('/api/employees', payload);

        pending.then(function () {
            closeModal();
            App.toast(editingId ? '修改成功' : '新增成功', 'ok');
            loadList();
        }).catch(function (error) {
            showFormError(error.message || '保存失败');
        }).then(function () {
            el.btnSubmit.disabled = false;
        });
    }

    function removeRecord(id, name) {
        if (!window.confirm('确认删除员工「' + name + '」吗？删除后不可恢复。')) {
            return;
        }
        App.del('/api/employees/' + id).then(function () {
            App.toast('删除成功', 'ok');
            // 当前页仅剩这一条时回退一页，避免停在空页
            if (state.loadedCount <= 1 && state.current > 1) {
                state.current = state.current - 1;
            }
            loadList();
        }).catch(function (error) {
            App.toast(error.message || '删除失败', 'err');
        });
    }

    /* ---------------------------------------------------------------- 事件 */

    function bindEvents() {
        el.btnSearch.addEventListener('click', function () {
            state.keyword = el.keyword.value.trim();
            state.department = el.filterDepartment.value;
            state.status = el.filterStatus.value;
            state.current = 1;
            loadList();
        });

        el.btnReset.addEventListener('click', function () {
            el.keyword.value = '';
            el.filterDepartment.value = '';
            el.filterStatus.value = '';
            state.keyword = '';
            state.department = '';
            state.status = '';
            state.current = 1;
            loadList();
        });

        el.keyword.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                el.btnSearch.click();
            }
        });

        el.filterDepartment.addEventListener('change', function () {
            el.btnSearch.click();
        });
        el.filterStatus.addEventListener('change', function () {
            el.btnSearch.click();
        });

        el.btnCreate.addEventListener('click', function () {
            openModal(null);
        });

        // 部门变化时重新加载该部门下的职级（职级归属部门），已选职级随之清空
        el.department.addEventListener('change', function () {
            loadRanks(fieldValue('department'), null);
        });

        el.pageSize.addEventListener('change', function () {
            state.size = Number(el.pageSize.value) || 10;
            state.current = 1;
            loadList();
        });

        el.btnPrev.addEventListener('click', function () {
            if (state.current > 1) {
                state.current = state.current - 1;
                loadList();
            }
        });

        el.btnNext.addEventListener('click', function () {
            var pages = Math.max(1, Math.ceil(state.total / state.size));
            if (state.current < pages) {
                state.current = state.current + 1;
                loadList();
            }
        });

        // 表格内的编辑/删除按钮（事件代理，避免重复绑定）
        el.body.addEventListener('click', function (event) {
            var target = event.target;
            if (!target.closest) {
                return;
            }
            var editBtn = target.closest('[data-edit]');
            if (editBtn) {
                var editId = Number(editBtn.getAttribute('data-edit'));
                App.get('/api/employees/' + editId).then(function (record) {
                    openModal(record);
                }).catch(function (error) {
                    App.toast(error.message, 'err');
                });
                return;
            }
            var salaryBtn = target.closest('[data-salary]');
            if (salaryBtn) {
                openSalaryModal(
                    Number(salaryBtn.getAttribute('data-salary')),
                    salaryBtn.getAttribute('data-work-no') || '',
                    salaryBtn.getAttribute('data-name') || '');
                return;
            }
            var removeBtn = target.closest('[data-remove]');
            if (removeBtn) {
                var row = removeBtn.closest('tr');
                var nameCell = row ? row.children[1] : null;
                var name = nameCell ? nameCell.textContent : '该员工';
                removeRecord(Number(removeBtn.getAttribute('data-remove')), name);
            }
        });

        el.form.addEventListener('submit', submitForm);

        el.modal.addEventListener('click', function (event) {
            var target = event.target;
            if (target.closest && target.closest('[data-close]')) {
                closeModal();
            }
        });

        // 薪酬弹窗：月薪变化即换算（input 覆盖键入与上下箭头，change 覆盖粘贴与清空）
        el.salaryForm.addEventListener('submit', submitSalary);
        el.salaryMonth.addEventListener('input', syncDerivedAmounts);
        el.salaryMonth.addEventListener('change', syncDerivedAmounts);

        el.salaryModal.addEventListener('click', function (event) {
            var target = event.target;
            if (target.closest && target.closest('[data-close]')) {
                closeSalaryModal();
            }
        });

        document.addEventListener('keydown', function (event) {
            if (event.key !== 'Escape') {
                return;
            }
            // 两个弹窗各自判断自己的显隐，不做「谁叠在谁上面」的假设
            if (!el.salaryModal.hidden) {
                closeSalaryModal();
            } else if (!el.modal.hidden) {
                closeModal();
            }
        });
    }

    bindEvents();
    loadList();
})(window, document);
