/* ==========================================================================
   部门管理页脚本：部门增删改查 + 部门职级增删改查，全部 ajax，无整页刷新
   约定：职级隶属于部门，职级操作统一以「部门」为入口（嵌套路由）
   ========================================================================== */
(function (window, document) {
    'use strict';

    var App = window.App;

    var state = {
        keyword: '',
        editingId: null,
        /** 当前打开职级管理的部门 { id, departName } */
        currentDepartment: null,
        /** 当前部门的职级列表缓存 */
        ranks: [],
        rankEditingId: null
    };

    /** 上级部门下拉的原始选项（页面首次渲染时缓存，编辑时用于排除自身） */
    var parentOptions = null;

    var el = {
        keyword: document.getElementById('keyword'),
        btnSearch: document.getElementById('btnSearch'),
        btnReset: document.getElementById('btnReset'),
        btnCreate: document.getElementById('btnCreate'),
        body: document.getElementById('departmentBody'),
        listSummary: document.getElementById('listSummary'),

        departmentModal: document.getElementById('departmentModal'),
        departmentModalTitle: document.getElementById('departmentModalTitle'),
        departmentForm: document.getElementById('departmentForm'),
        departmentError: document.getElementById('departmentError'),
        departName: document.getElementById('departName'),
        parentId: document.getElementById('parentId'),
        description: document.getElementById('description'),
        btnSubmitDepartment: document.getElementById('btnSubmitDepartment'),

        rankModal: document.getElementById('rankModal'),
        rankModalTitle: document.getElementById('rankModalTitle'),
        rankForm: document.getElementById('rankForm'),
        rankError: document.getElementById('rankError'),
        rankCode: document.getElementById('rankCode'),
        rankType: document.getElementById('rankType'),
        rankIntro: document.getElementById('rankIntro'),
        btnSubmitRank: document.getElementById('btnSubmitRank'),
        btnCancelRankEdit: document.getElementById('btnCancelRankEdit'),
        rankBody: document.getElementById('rankBody')
    };

    /* ------------------------------------------------------------ 通用工具 */

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

    function numberOrNull(id) {
        var raw = fieldValue(id);
        return raw === '' ? null : Number(raw);
    }

    /** 数量单元格：0 用弱化文本，正数用徽标。 */
    function countCell(value) {
        var count = value === null || value === undefined ? 0 : Number(value);
        return count > 0
            ? '<span class="badge badge--info">' + count + '</span>'
            : '<span class="text-muted">0</span>';
    }

    /* ------------------------------------------------------------ 部门列表 */

    function renderRows(records) {
        if (!records || records.length === 0) {
            el.body.innerHTML = '<tr><td colspan="8" class="table__empty">暂无部门数据</td></tr>';
            el.listSummary.textContent = '';
            return;
        }
        el.body.innerHTML = records.map(function (row) {
            return '<tr>'
                + '<td>' + App.dash(row.id) + '</td>'
                + '<td>' + App.dash(row.departName) + '</td>'
                + '<td>' + App.dash(row.parentName) + '</td>'
                + '<td>' + App.dash(row.description) + '</td>'
                + '<td>' + countCell(row.rankCount) + '</td>'
                + '<td>' + countCell(row.employeeCount) + '</td>'
                + '<td>' + App.dash(App.formatDateTime(row.createTime)) + '</td>'
                + '<td><div class="table__actions">'
                + '<button type="button" class="btn btn--sm" data-edit="' + row.id + '">编辑</button>'
                + '<button type="button" class="btn btn--sm" data-ranks="' + row.id + '">职级</button>'
                + '<button type="button" class="btn btn--sm btn--danger" data-remove="' + row.id + '">删除</button>'
                + '</div></td>'
                + '</tr>';
        }).join('');
        el.listSummary.textContent = '共 ' + records.length + ' 个部门';
    }

    function loadList() {
        var url = '/api/departments';
        if (state.keyword) {
            url += '?keyword=' + encodeURIComponent(state.keyword);
        }
        el.body.innerHTML = '<tr><td colspan="8" class="table__empty">正在加载…</td></tr>';
        return App.get(url).then(function (list) {
            renderRows(list);
        }).catch(function (error) {
            el.body.innerHTML = '<tr><td colspan="8" class="table__empty">加载失败：'
                + App.escapeHtml(error.message) + '</td></tr>';
            App.toast(error.message, 'err');
        });
    }

    /* ------------------------------------------------------------ 部门弹窗 */

    function initParentOptions() {
        parentOptions = Array.prototype.slice.call(el.parentId.options).map(function (option) {
            return {value: option.value, label: option.textContent};
        });
    }

    /** 重建上级部门下拉，编辑时排除自身（子孙由服务端校验兜底）。 */
    function fillParentOptions(excludeId) {
        var exclude = excludeId === null || excludeId === undefined ? null : String(excludeId);
        var html = '';
        (parentOptions || []).forEach(function (item) {
            if (exclude !== null && item.value === exclude) {
                return;
            }
            html += '<option value="' + App.escapeHtml(item.value) + '">'
                + App.escapeHtml(item.label) + '</option>';
        });
        el.parentId.innerHTML = html;
    }

    function openDepartmentModal(record) {
        state.editingId = record ? record.id : null;
        el.departmentModalTitle.textContent = record ? '修改部门' : '新增部门';
        el.departmentError.textContent = '';

        fillParentOptions(state.editingId);
        setField('departName', record ? record.departName : '');
        setField('parentId', record && record.parentId !== null && record.parentId !== undefined
            ? record.parentId : '');
        setField('description', record ? record.description : '');

        el.departmentModal.hidden = false;
        el.departName.focus();
    }

    function closeDepartmentModal() {
        el.departmentModal.hidden = true;
        state.editingId = null;
        el.departmentError.textContent = '';
    }

    function submitDepartmentForm(event) {
        event.preventDefault();

        var payload = {
            departName: fieldValue('departName'),
            parentId: numberOrNull('parentId'),
            description: fieldValue('description') || null
        };
        if (!payload.departName) {
            el.departmentError.textContent = '请填写部门名称';
            return;
        }

        var editingId = state.editingId;
        el.departmentError.textContent = '';
        el.btnSubmitDepartment.disabled = true;

        var pending = editingId
            ? App.put('/api/departments/' + editingId, payload)
            : App.post('/api/departments', payload);

        pending.then(function () {
            closeDepartmentModal();
            App.toast(editingId ? '部门修改成功' : '部门新增成功', 'ok');
            loadList();
        }).catch(function (error) {
            el.departmentError.textContent = error.message || '保存失败';
        }).then(function () {
            el.btnSubmitDepartment.disabled = false;
        });
    }

    function removeDepartment(id, name) {
        if (!window.confirm('确认删除部门「' + name + '」吗？\n'
                + '若该部门下仍有子部门、职级或员工，删除会被拒绝。')) {
            return;
        }
        App.del('/api/departments/' + id).then(function () {
            App.toast('部门删除成功', 'ok');
            loadList();
        }).catch(function (error) {
            App.toast(error.message || '删除失败', 'err');
        });
    }

    /* ------------------------------------------------------------ 职级弹窗 */

    function renderRankRows(records) {
        if (!records || records.length === 0) {
            el.rankBody.innerHTML = '<tr><td colspan="5" class="table__empty">'
                + '该部门下暂无职级，可在上方表单添加</td></tr>';
            return;
        }
        el.rankBody.innerHTML = records.map(function (row) {
            return '<tr>'
                + '<td>' + App.dash(row.id) + '</td>'
                + '<td>' + App.dash(row.code) + '</td>'
                + '<td>' + App.dash(row.typeText) + '</td>'
                + '<td>' + App.dash(row.intro) + '</td>'
                + '<td><div class="table__actions">'
                + '<button type="button" class="btn btn--sm" data-rank-edit="' + row.id + '">编辑</button>'
                + '<button type="button" class="btn btn--sm btn--danger" data-rank-remove="'
                + row.id + '">删除</button>'
                + '</div></td>'
                + '</tr>';
        }).join('');
    }

    function loadRanks() {
        var departmentId = state.currentDepartment.id;
        el.rankBody.innerHTML = '<tr><td colspan="5" class="table__empty">正在加载…</td></tr>';
        return App.get('/api/departments/' + departmentId + '/ranks').then(function (list) {
            state.ranks = list || [];
            renderRankRows(state.ranks);
        }).catch(function (error) {
            el.rankBody.innerHTML = '<tr><td colspan="5" class="table__empty">加载失败：'
                + App.escapeHtml(error.message) + '</td></tr>';
            App.toast(error.message, 'err');
        });
    }

    function openRankModal(department) {
        state.currentDepartment = department;
        resetRankForm();
        el.rankError.textContent = '';
        el.rankModalTitle.textContent = department.departName + ' · 职级管理';
        el.rankModal.hidden = false;
        loadRanks();
    }

    function closeRankModal() {
        el.rankModal.hidden = true;
        state.currentDepartment = null;
        state.ranks = [];
        resetRankForm();
        el.rankError.textContent = '';
        // 职级变更会影响部门列表的职级数，关闭时统一刷新一次
        loadList();
    }

    function resetRankForm() {
        state.rankEditingId = null;
        setField('rankCode', '');
        setField('rankIntro', '');
        if (el.rankType.options.length > 0) {
            el.rankType.selectedIndex = 0;
        }
        el.btnSubmitRank.textContent = '添加职级';
        el.btnCancelRankEdit.hidden = true;
    }

    function startRankEdit(rankId) {
        var target = null;
        state.ranks.forEach(function (item) {
            if (Number(item.id) === Number(rankId)) {
                target = item;
            }
        });
        if (!target) {
            return;
        }
        state.rankEditingId = target.id;
        setField('rankCode', target.code);
        setField('rankType', target.type);
        setField('rankIntro', target.intro);
        el.btnSubmitRank.textContent = '保存修改';
        el.btnCancelRankEdit.hidden = false;
        el.rankCode.focus();
    }

    function submitRankForm(event) {
        event.preventDefault();

        var payload = {
            code: fieldValue('rankCode'),
            type: numberOrNull('rankType'),
            intro: fieldValue('rankIntro') || null
        };
        if (!payload.code) {
            el.rankError.textContent = '请填写职级编码';
            return;
        }
        if (!payload.type) {
            el.rankError.textContent = '请选择职级序列';
            return;
        }

        var departmentId = state.currentDepartment.id;
        var editingId = state.rankEditingId;
        el.rankError.textContent = '';
        el.btnSubmitRank.disabled = true;

        var pending = editingId
            ? App.put('/api/departments/' + departmentId + '/ranks/' + editingId, payload)
            : App.post('/api/departments/' + departmentId + '/ranks', payload);

        pending.then(function () {
            resetRankForm();
            App.toast(editingId ? '职级修改成功' : '职级添加成功', 'ok');
            loadRanks();
            loadList();
        }).catch(function (error) {
            el.rankError.textContent = error.message || '保存失败';
        }).then(function () {
            el.btnSubmitRank.disabled = false;
        });
    }

    function removeRank(rankId, code) {
        if (!window.confirm('确认删除职级「' + code + '」吗？\n若已有员工使用该职级，删除会被拒绝。')) {
            return;
        }
        var departmentId = state.currentDepartment.id;
        App.del('/api/departments/' + departmentId + '/ranks/' + rankId).then(function () {
            App.toast('职级删除成功', 'ok');
            if (state.rankEditingId !== null && Number(state.rankEditingId) === Number(rankId)) {
                resetRankForm();
            }
            loadRanks();
            loadList();
        }).catch(function (error) {
            App.toast(error.message || '删除失败', 'err');
        });
    }

    /* ---------------------------------------------------------------- 事件 */

    function bindEvents() {
        el.btnSearch.addEventListener('click', function () {
            state.keyword = el.keyword.value.trim();
            loadList();
        });

        el.btnReset.addEventListener('click', function () {
            el.keyword.value = '';
            state.keyword = '';
            loadList();
        });

        el.keyword.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                el.btnSearch.click();
            }
        });

        el.btnCreate.addEventListener('click', function () {
            openDepartmentModal(null);
        });

        // 部门表格内的编辑 / 职级 / 删除 按钮（事件代理）
        el.body.addEventListener('click', function (event) {
            var target = event.target;
            if (!target.closest) {
                return;
            }

            var editBtn = target.closest('[data-edit]');
            if (editBtn) {
                var editId = Number(editBtn.getAttribute('data-edit'));
                App.get('/api/departments/' + editId).then(function (record) {
                    openDepartmentModal(record);
                }).catch(function (error) {
                    App.toast(error.message, 'err');
                });
                return;
            }

            var rankBtn = target.closest('[data-ranks]');
            if (rankBtn) {
                var row = rankBtn.closest('tr');
                var nameCell = row ? row.children[1] : null;
                openRankModal({
                    id: Number(rankBtn.getAttribute('data-ranks')),
                    departName: nameCell ? nameCell.textContent : '该部门'
                });
                return;
            }

            var removeBtn = target.closest('[data-remove]');
            if (removeBtn) {
                var removeRow = removeBtn.closest('tr');
                var nameNode = removeRow ? removeRow.children[1] : null;
                removeDepartment(Number(removeBtn.getAttribute('data-remove')),
                    nameNode ? nameNode.textContent : '该部门');
            }
        });

        el.departmentForm.addEventListener('submit', submitDepartmentForm);

        el.departmentModal.addEventListener('click', function (event) {
            if (event.target.closest && event.target.closest('[data-close]')) {
                closeDepartmentModal();
            }
        });

        // 职级表格内的编辑 / 删除 按钮（事件代理）
        el.rankBody.addEventListener('click', function (event) {
            var target = event.target;
            if (!target.closest) {
                return;
            }
            var editBtn = target.closest('[data-rank-edit]');
            if (editBtn) {
                startRankEdit(Number(editBtn.getAttribute('data-rank-edit')));
                return;
            }
            var removeBtn = target.closest('[data-rank-remove]');
            if (removeBtn) {
                var row = removeBtn.closest('tr');
                var codeCell = row ? row.children[1] : null;
                removeRank(Number(removeBtn.getAttribute('data-rank-remove')),
                    codeCell ? codeCell.textContent : '该职级');
            }
        });

        el.rankForm.addEventListener('submit', submitRankForm);

        el.btnCancelRankEdit.addEventListener('click', function () {
            resetRankForm();
            el.rankError.textContent = '';
        });

        el.rankModal.addEventListener('click', function (event) {
            if (event.target.closest && event.target.closest('[data-close]')) {
                closeRankModal();
            }
        });

        document.addEventListener('keydown', function (event) {
            if (event.key !== 'Escape') {
                return;
            }
            if (!el.rankModal.hidden) {
                closeRankModal();
                return;
            }
            if (!el.departmentModal.hidden) {
                closeDepartmentModal();
            }
        });
    }

    initParentOptions();
    bindEvents();
    loadList();
})(window, document);
