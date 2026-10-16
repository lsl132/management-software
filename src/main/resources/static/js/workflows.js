/* ==========================================================================
   流程管理页脚本（权限字典里的「审批规则」）
   约定：
     - 一条规则 = 流程信息 + 有序节点链，整体提交（不走「先存流程再存节点」两步）
     - 节点链固定为「起始 → 若干审批 → 结束」：起始/结束各一个、守在首尾，不可增删移动
     - 节点顺序 = 行在列表里的顺序，不额外维护 sort，保存时由服务端按下标重排
     - 编辑时按节点 id 差量同步（有 id 的改、无 id 的加、没提交的删），避免整批重建
   ========================================================================== */
(function (window, document) {
    'use strict';

    var App = window.App;

    var NODE_START = 0;
    var NODE_APPROVAL = 1;
    var NODE_END = 2;

    var APPROVAL_ASSIGNEE = 1;
    var APPROVAL_SUPERIOR = 2;
    var APPROVAL_ROLE = 3;

    var APPROVAL_MODE_ANY = 1;

    var STATUS_ENABLED = 1;
    var STATUS_DISABLED = 2;

    var state = {
        current: 1,
        size: 10,
        keyword: '',
        status: '',
        total: 0,
        editingId: null,
        /** 详情弹窗当前展示的流程（点「编辑」时复用它，省一次请求） */
        detail: null
    };

    /** 审批人（系统账号）选项，首次进入页面拉一次后常驻 */
    var userOptions = [];
    /** 审批角色选项，来自模板里服务端渲染的隐藏数据源 */
    var roleOptions = [];

    var el = {
        keyword: document.getElementById('keyword'),
        filterStatus: document.getElementById('filterStatus'),
        btnSearch: document.getElementById('btnSearch'),
        btnReset: document.getElementById('btnReset'),
        btnCreate: document.getElementById('btnCreate'),
        body: document.getElementById('flowBody'),
        pagerInfo: document.getElementById('pagerInfo'),
        pageNow: document.getElementById('pageNow'),
        pageSize: document.getElementById('pageSize'),
        btnPrev: document.getElementById('btnPrev'),
        btnNext: document.getElementById('btnNext'),

        modal: document.getElementById('flowModal'),
        modalTitle: document.getElementById('flowModalTitle'),
        form: document.getElementById('flowForm'),
        error: document.getElementById('flowError'),
        btnSubmit: document.getElementById('btnSubmitFlow'),
        workflowName: document.getElementById('workflowName'),
        icon: document.getElementById('icon'),
        status: document.getElementById('status'),
        description: document.getElementById('description'),
        nodeList: document.getElementById('nodeList'),
        btnAddNode: document.getElementById('btnAddNode'),

        detailModal: document.getElementById('detailModal'),
        detailTitle: document.getElementById('detailModalTitle'),
        detailName: document.getElementById('detailName'),
        detailDescription: document.getElementById('detailDescription'),
        detailStatus: document.getElementById('detailStatus'),
        detailNodes: document.getElementById('detailNodes'),
        detailTime: document.getElementById('detailTime'),
        btnDetailEdit: document.getElementById('btnDetailEdit')
    };

    /* ------------------------------------------------------------ 列表 */

    function statusBadge(status) {
        var enabled = Number(status) === STATUS_ENABLED;
        return '<span class="badge ' + (enabled ? 'badge--ok' : 'badge--muted') + '">'
            + (enabled ? '启用' : '禁用') + '</span>';
    }

    function actionCell(row) {
        var enabled = Number(row.status) === STATUS_ENABLED;
        var toggleTarget = enabled ? STATUS_DISABLED : STATUS_ENABLED;
        return '<div class="table__actions">'
            + '<button type="button" class="btn btn--sm" data-detail="' + row.id + '">详情</button>'
            + '<button type="button" class="btn btn--sm" data-edit="' + row.id + '">编辑</button>'
            + '<button type="button" class="btn btn--sm" data-toggle="' + row.id + '"'
            + ' data-target="' + toggleTarget + '">' + (enabled ? '禁用' : '启用') + '</button>'
            + '<button type="button" class="btn btn--sm btn--danger" data-delete="' + row.id + '">删除</button>'
            + '</div>';
    }

    function chainCell(row) {
        if (!row.nodeSummary) {
            return '<span class="text-muted">尚未编排节点</span>';
        }
        return '<span class="flow-chain" title="' + App.escapeHtml(row.nodeSummary) + '">'
            + App.escapeHtml(row.nodeSummary) + '</span>';
    }

    function renderRows(records) {
        if (!records || records.length === 0) {
            el.body.innerHTML = '<tr><td colspan="7" class="table__empty">暂无流程数据，点右上角「新增流程」创建第一条审批规则</td></tr>';
            return;
        }
        el.body.innerHTML = records.map(function (row) {
            return '<tr>'
                + '<td>' + App.dash(row.id) + '</td>'
                + '<td>' + App.dash(row.workflowName) + '</td>'
                + '<td>' + App.dash(row.description) + '</td>'
                + '<td>' + chainCell(row) + '</td>'
                + '<td>' + statusBadge(row.status) + '</td>'
                + '<td>' + App.dash(App.formatDateTime(row.updateTime)) + '</td>'
                + '<td>' + actionCell(row) + '</td>'
                + '</tr>';
        }).join('');
    }

    function renderPager() {
        var pages = Math.max(1, Math.ceil(Number(state.total) / Number(state.size)));
        el.pagerInfo.textContent = '共 ' + state.total + ' 条';
        el.pageNow.textContent = state.current + ' / ' + pages;
        el.btnPrev.disabled = state.current <= 1;
        el.btnNext.disabled = state.current >= pages;
    }

    function loadList() {
        var params = new window.URLSearchParams();
        params.set('current', state.current);
        params.set('size', state.size);
        if (state.keyword) {
            params.set('keyword', state.keyword);
        }
        if (state.status !== '') {
            params.set('status', state.status);
        }

        el.body.innerHTML = '<tr><td colspan="7" class="table__empty">正在加载…</td></tr>';
        return App.get('/api/workflows?' + params.toString()).then(function (data) {
            state.total = Number(data.total);
            state.current = Number(data.current);
            state.size = Number(data.size);
            renderRows(data.records);
            renderPager();
        }).catch(function (error) {
            el.body.innerHTML = '<tr><td colspan="7" class="table__empty">加载失败：'
                + App.escapeHtml(error.message) + '</td></tr>';
            App.toast(error.message, 'err');
        });
    }

    /* -------------------------------------------------------- 节点编排 */

    function approvalRows() {
        return Array.prototype.slice.call(el.nodeList.querySelectorAll('.node-row[data-type="1"]'));
    }

    function approvalTypeOptions(selected) {
        var items = [
            {value: APPROVAL_ASSIGNEE, label: '指定人员'},
            // 直属上级需要「员工的上级是谁」这份数据，employee 表暂无上级字段：
            // 配出来也找不到该由谁审批，所以在这里就拦住，而不是等到跑流程时才失败
            {value: APPROVAL_SUPERIOR, label: '直属上级（暂不支持）', disabled: true},
            {value: APPROVAL_ROLE, label: '指定角色'}
        ];
        return items.map(function (item) {
            return '<option value="' + item.value + '"'
                + (item.disabled ? ' disabled' : '')
                + (Number(selected) === item.value ? ' selected' : '')
                + '>' + item.label + '</option>';
        }).join('');
    }

    function approvalModeOptions(selected) {
        var value = selected === null || selected === undefined ? APPROVAL_MODE_ANY : Number(selected);
        return [
            {value: 1, label: '或签（一人通过即生效）'},
            {value: 2, label: '会签（全员通过才生效）'}
        ].map(function (item) {
            return '<option value="' + item.value + '"'
                + (value === item.value ? ' selected' : '')
                + '>' + item.label + '</option>';
        }).join('');
    }

    function nodeRowHtml(node) {
        var type = Number(node.type);
        if (type === NODE_START || type === NODE_END) {
            var isStart = type === NODE_START;
            return '<div class="node-row node-row--fixed" data-type="' + type + '">'
                + '<span class="node-row__badge node-row__badge--' + (isStart ? 'start' : 'end') + '">'
                + (isStart ? '起始' : '结束') + '</span>'
                + '<input class="field__input" data-field="nodeName" type="text" maxlength="128"'
                + ' value="' + App.escapeHtml(node.nodeName) + '"/>'
                + '<p class="node-row__note">'
                + (isStart ? '发起人提交，无需审批配置' : '流程归档，无需审批配置')
                + '</p>'
                + '</div>';
        }

        var nodeId = node.id === null || node.id === undefined ? '' : node.id;
        return '<div class="node-row" data-type="' + NODE_APPROVAL + '" data-node-id="' + nodeId + '">'
            + '<span class="node-row__badge node-row__badge--approval">审批</span>'
            + '<input class="field__input" data-field="nodeName" type="text" maxlength="128"'
            + ' value="' + App.escapeHtml(node.nodeName) + '"/>'
            + '<select class="field__select" data-field="approvalType">'
            + approvalTypeOptions(node.approvalType) + '</select>'
            + '<select class="field__select node-row__picker" data-field="approvalValues"'
            + ' multiple size="4"></select>'
            + '<select class="field__select" data-field="approvalMode">'
            + approvalModeOptions(node.approvalMode) + '</select>'
            + '<div class="node-row__actions">'
            + '<button type="button" class="btn btn--sm" data-move="-1" title="上移">↑</button>'
            + '<button type="button" class="btn btn--sm" data-move="1" title="下移">↓</button>'
            + '<button type="button" class="btn btn--sm btn--danger" data-remove="1">删除</button>'
            + '</div>'
            + '</div>';
    }

    /**
     * 填充「审批人 / 审批角色」多选下拉。
     *
     * <p>已配置但当前不在候选里的值（账号被冻结、角色被删）会补一个「#id（已不可选）」并保持选中：
     * 不补的话，用户只是打开弹窗再保存，这条审批人就被静默抹掉了——这种丢失不会报错，
     * 只会体现在某天有人发现流程少了一环。
     */
    function fillPicker(row, values, approvalType) {
        var picker = row.querySelector('[data-field="approvalValues"]');
        if (!picker) {
            return;
        }
        var type = Number(approvalType);
        var options = type === APPROVAL_ROLE ? roleOptions : userOptions;
        var selected = {};
        (values || []).forEach(function (value) {
            selected[String(value)] = true;
        });

        var known = {};
        var html = (options || []).map(function (item) {
            known[String(item.value)] = true;
            return '<option value="' + App.escapeHtml(item.value) + '">'
                + App.escapeHtml(item.label) + '</option>';
        }).join('');
        (values || []).forEach(function (value) {
            if (known[String(value)]) {
                return;
            }
            html += '<option value="' + App.escapeHtml(value) + '">#'
                + App.escapeHtml(value) + '（已不可选）</option>';
        });

        picker.innerHTML = html;
        Array.prototype.slice.call(picker.options).forEach(function (option) {
            option.selected = !!selected[option.value];
        });
        picker.setAttribute('data-source', type === APPROVAL_ROLE ? 'role' : 'user');
    }

    function renderNodes(nodes) {
        el.nodeList.innerHTML = nodes.map(nodeRowHtml).join('');

        var approvalNodes = nodes.filter(function (node) {
            return Number(node.type) === NODE_APPROVAL;
        });
        approvalRows().forEach(function (row, index) {
            var node = approvalNodes[index] || {};
            fillPicker(row, node.approvalValues, node.approvalType === undefined ? APPROVAL_ASSIGNEE : node.approvalType);
        });
        refreshNodePositions();
    }

    /** 只有「第一个审批节点不能再上移、最后一个不能再下移」，起始与结束节点永远不动。 */
    function refreshNodePositions() {
        var rows = approvalRows();
        rows.forEach(function (row, index) {
            var up = row.querySelector('[data-move="-1"]');
            var down = row.querySelector('[data-move="1"]');
            if (up) {
                up.disabled = index === 0;
            }
            if (down) {
                down.disabled = index === rows.length - 1;
            }
        });
    }

    function addNodeRow() {
        var endRow = el.nodeList.querySelector('.node-row[data-type="2"]');
        var wrapper = document.createElement('div');
        wrapper.innerHTML = nodeRowHtml({
            id: null,
            nodeName: '部门审批',
            type: NODE_APPROVAL,
            approvalType: APPROVAL_ASSIGNEE,
            approvalMode: APPROVAL_MODE_ANY
        });
        var row = wrapper.querySelector('.node-row');
        el.nodeList.insertBefore(row, endRow);
        fillPicker(row, [], APPROVAL_ASSIGNEE);
        refreshNodePositions();
        row.querySelector('[data-field="nodeName"]').focus();
    }

    /** 上移 / 下移：只在审批节点区间内交换，越过起始或结束会被拦下。 */
    function moveRow(row, direction) {
        var rows = Array.prototype.slice.call(el.nodeList.querySelectorAll('.node-row'));
        var index = rows.indexOf(row);
        var target = index + direction;
        if (target < 1 || target > rows.length - 2) {
            return;
        }
        if (direction < 0) {
            el.nodeList.insertBefore(row, rows[target]);
        } else {
            el.nodeList.insertBefore(rows[target], row);
        }
        refreshNodePositions();
    }

    function rowFieldValue(row, field) {
        var node = row.querySelector('[data-field="' + field + '"]');
        return node ? String(node.value).trim() : '';
    }

    function rowNumber(row, field) {
        var raw = rowFieldValue(row, field);
        return raw === '' ? null : Number(raw);
    }

    function pickerValues(row) {
        var picker = row.querySelector('[data-field="approvalValues"]');
        if (!picker) {
            return [];
        }
        return Array.prototype.slice.call(picker.options)
            .filter(function (option) {
                return option.selected;
            })
            .map(function (option) {
                return Number(option.value);
            });
    }

    function collectNodes() {
        return Array.prototype.slice.call(el.nodeList.querySelectorAll('.node-row')).map(function (row) {
            var type = Number(row.getAttribute('data-type'));
            var nodeId = row.getAttribute('data-node-id');
            var node = {
                id: nodeId ? Number(nodeId) : null,
                nodeName: rowFieldValue(row, 'nodeName'),
                type: type
            };
            if (type === NODE_APPROVAL) {
                node.approvalType = rowNumber(row, 'approvalType');
                node.approvalMode = rowNumber(row, 'approvalMode');
                node.approvalValues = pickerValues(row);
            }
            return node;
        });
    }

    /** 前端先校验一遍，只为省掉一次注定失败的请求；服务端有同一套规则做真正的兜底。 */
    function validateNodes(nodes) {
        if (nodes.length < 2) {
            return '流程至少要包含起始与结束两个节点';
        }
        if (!nodes[0].nodeName) {
            return '请填写起始节点名称';
        }
        if (!nodes[nodes.length - 1].nodeName) {
            return '请填写结束节点名称';
        }
        var approvals = nodes.filter(function (node) {
            return node.type === NODE_APPROVAL;
        });
        for (var i = 0; i < approvals.length; i++) {
            var node = approvals[i];
            var position = '第 ' + (i + 1) + ' 个审批节点';
            if (!node.nodeName) {
                return position + '未填写名称';
            }
            if (!node.approvalType) {
                return position + '未选择审批方式';
            }
            if (!node.approvalMode) {
                return position + '未选择审批模式';
            }
            if (!node.approvalValues || node.approvalValues.length === 0) {
                return position + (node.approvalType === APPROVAL_ROLE ? '未指定审批角色' : '未指定审批人');
            }
        }
        return null;
    }

    /* ------------------------------------------------------------ 表单弹窗 */

    function defaultNodes() {
        return [
            {id: null, nodeName: '发起申请', type: NODE_START},
            {id: null, nodeName: '审批完成', type: NODE_END}
        ];
    }

    function setField(id, value) {
        var node = document.getElementById(id);
        if (node) {
            node.value = value === null || value === undefined ? '' : String(value);
        }
    }

    function openModal(record) {
        state.editingId = record ? record.id : null;
        el.modalTitle.textContent = record ? '编辑流程' : '新增流程';
        el.error.textContent = '';
        setField('workflowName', record ? record.workflowName : '');
        setField('icon', record ? record.icon : '');
        setField('description', record ? record.description : '');
        setField('status', record ? record.status : STATUS_ENABLED);

        var nodes = record && record.nodes && record.nodes.length ? record.nodes : defaultNodes();
        el.modal.hidden = false;
        // 等审批人选项就绪再渲染节点行：候选还没拉回来就渲染，多选下拉会是空的，
        // 用户会以为「审批人本来就没有」而直接保存
        usersReady.then(function () {
            renderNodes(nodes);
        });
        el.workflowName.focus();
    }

    function closeModal() {
        el.modal.hidden = true;
        state.editingId = null;
        el.error.textContent = '';
    }

    function submitForm(event) {
        event.preventDefault();

        var nodes = collectNodes();
        var payload = {
            workflowName: String(el.workflowName.value).trim(),
            icon: String(el.icon.value).trim() || null,
            description: String(el.description.value).trim() || null,
            status: el.status.value === '' ? null : Number(el.status.value),
            nodes: nodes
        };

        if (!payload.workflowName) {
            el.error.textContent = '请填写流程名称';
            return;
        }
        if (!payload.status) {
            el.error.textContent = '请选择流程状态';
            return;
        }
        var invalid = validateNodes(nodes);
        if (invalid) {
            el.error.textContent = invalid;
            return;
        }

        var editingId = state.editingId;
        el.error.textContent = '';
        el.btnSubmit.disabled = true;

        var pending = editingId
            ? App.put('/api/workflows/' + editingId, payload)
            : App.post('/api/workflows', payload);

        pending.then(function () {
            closeModal();
            App.toast(editingId ? '流程修改成功' : '流程创建成功', 'ok');
            loadList();
        }).catch(function (error) {
            el.error.textContent = error.message || '保存失败';
        }).then(function () {
            el.btnSubmit.disabled = false;
        });
    }

    /* ------------------------------------------------------------ 详情弹窗 */

    function nodeMeta(node) {
        if (Number(node.type) !== NODE_APPROVAL) {
            return '—';
        }
        var parts = [];
        if (node.approvalTypeText) {
            parts.push(node.approvalTypeText + '：' + (node.approvalValueText || '—'));
        }
        if (node.approvalModeText) {
            parts.push(node.approvalModeText);
        }
        return parts.join(' · ') || '—';
    }

    function nodeChainHtml(nodes) {
        if (!nodes || nodes.length === 0) {
            return '<span class="text-muted">尚未编排节点</span>';
        }
        return '<div class="node-chain">' + nodes.map(function (node) {
            var type = Number(node.type);
            var badgeClass = type === NODE_START ? 'start' : (type === NODE_END ? 'end' : 'approval');
            var badgeText = type === NODE_START ? '起始' : (type === NODE_END ? '结束' : '审批');
            return '<div class="node-chain__item">'
                + '<span class="node-row__badge node-row__badge--' + badgeClass + '">' + badgeText + '</span>'
                + '<span class="node-chain__name">' + App.dash(node.nodeName) + '</span>'
                + '<span class="node-chain__meta">' + App.escapeHtml(nodeMeta(node)) + '</span>'
                + '</div>';
        }).join('') + '</div>';
    }

    function openDetail(id) {
        App.get('/api/workflows/' + id).then(function (record) {
            state.detail = record;
            el.detailTitle.textContent = '流程详情 · ' + record.workflowName;
            el.detailName.textContent = record.workflowName;
            el.detailDescription.textContent = record.description || '—';
            el.detailStatus.innerHTML = statusBadge(record.status)
                + ' <span class="text-muted">共 ' + record.nodeCount + ' 个节点，其中 '
                + record.approvalNodeCount + ' 个审批节点</span>';
            el.detailNodes.innerHTML = nodeChainHtml(record.nodes);
            el.detailTime.textContent = App.formatDateTime(record.createTime)
                + ' / ' + App.formatDateTime(record.updateTime);
            el.detailModal.hidden = false;
        }).catch(function (error) {
            App.toast(error.message, 'err');
        });
    }

    /* ------------------------------------------------------------ 行内动作 */

    function rowText(id, column) {
        var button = el.body.querySelector('[data-edit="' + id + '"]');
        var row = button ? button.closest('tr') : null;
        var cell = row ? row.children[column] : null;
        return cell ? cell.textContent.trim() : '';
    }

    function changeStatus(id, target) {
        var name = rowText(id, 1);
        var text = Number(target) === STATUS_ENABLED ? '启用' : '禁用';
        if (!window.confirm('确认' + text + '流程「' + name + '」吗？\n'
                + (Number(target) === STATUS_ENABLED
                    ? '启用后可以发起该流程的审批。'
                    : '禁用后不能再发起该流程的审批，已在途中的单子不受影响。'))) {
            return;
        }
        App.post('/api/workflows/' + id + '/status', {status: Number(target)}).then(function () {
            App.toast('流程已' + text, 'ok');
            loadList();
        }).catch(function (error) {
            App.toast(error.message || '操作失败', 'err');
        });
    }

    function removeFlow(id) {
        var name = rowText(id, 1);
        if (!window.confirm('确认删除流程「' + name + '」吗？\n'
                + '该流程的全部节点会一并删除，且不可恢复。')) {
            return;
        }
        App.del('/api/workflows/' + id).then(function () {
            App.toast('流程已删除', 'ok');
            loadList();
        }).catch(function (error) {
            App.toast(error.message || '删除失败', 'err');
        });
    }

    /* ------------------------------------------------------------ 事件 */

    function bindEvents() {
        el.btnSearch.addEventListener('click', function () {
            state.keyword = el.keyword.value.trim();
            state.status = el.filterStatus.value;
            state.current = 1;
            loadList();
        });

        el.btnReset.addEventListener('click', function () {
            el.keyword.value = '';
            el.filterStatus.value = '';
            state.keyword = '';
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

        el.btnCreate.addEventListener('click', function () {
            openModal(null);
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
            var pages = Math.max(1, Math.ceil(Number(state.total) / Number(state.size)));
            if (state.current < pages) {
                state.current = state.current + 1;
                loadList();
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
                App.get('/api/workflows/' + editId).then(function (record) {
                    openModal(record);
                }).catch(function (error) {
                    App.toast(error.message, 'err');
                });
                return;
            }
            var detailBtn = target.closest('[data-detail]');
            if (detailBtn) {
                openDetail(detailBtn.getAttribute('data-detail'));
                return;
            }
            var toggleBtn = target.closest('[data-toggle]');
            if (toggleBtn) {
                changeStatus(toggleBtn.getAttribute('data-toggle'), toggleBtn.getAttribute('data-target'));
                return;
            }
            var deleteBtn = target.closest('[data-delete]');
            if (deleteBtn) {
                removeFlow(deleteBtn.getAttribute('data-delete'));
            }
        });

        el.btnAddNode.addEventListener('click', addNodeRow);

        el.nodeList.addEventListener('click', function (event) {
            var target = event.target;
            if (!target.closest) {
                return;
            }
            var row = target.closest('.node-row');
            if (!row) {
                return;
            }
            var moveBtn = target.closest('[data-move]');
            if (moveBtn) {
                moveRow(row, Number(moveBtn.getAttribute('data-move')));
                return;
            }
            if (target.closest('[data-remove]')) {
                row.parentNode.removeChild(row);
                refreshNodePositions();
            }
        });

        // 审批方式一改，候选对象就从「用户」切到「角色」（反之亦然），原有的选中值不再适用
        el.nodeList.addEventListener('change', function (event) {
            var target = event.target;
            if (!target || target.getAttribute('data-field') !== 'approvalType') {
                return;
            }
            var row = target.closest('.node-row');
            if (row) {
                fillPicker(row, [], target.value);
            }
        });

        el.form.addEventListener('submit', submitForm);

        el.modal.addEventListener('click', function (event) {
            if (event.target.closest && event.target.closest('[data-close]')) {
                closeModal();
            }
        });

        el.detailModal.addEventListener('click', function (event) {
            if (event.target.closest && event.target.closest('[data-close]')) {
                el.detailModal.hidden = true;
                return;
            }
            if (event.target.closest && event.target.closest('#btnDetailEdit') && state.detail) {
                el.detailModal.hidden = true;
                openModal(state.detail);
            }
        });

        document.addEventListener('keydown', function (event) {
            if (event.key !== 'Escape') {
                return;
            }
            if (!el.detailModal.hidden) {
                el.detailModal.hidden = true;
                return;
            }
            if (!el.modal.hidden) {
                closeModal();
            }
        });
    }

    /* ------------------------------------------------------------ 启动 */

    function readRoleOptions() {
        var source = document.getElementById('roleOptionSource');
        if (!source) {
            return [];
        }
        return Array.prototype.slice.call(source.options).map(function (option) {
            return {value: option.value, label: option.textContent};
        });
    }

    roleOptions = readRoleOptions();
    bindEvents();

    // 审批人列表先拉一次：没有它，审批节点的多选下拉是空的，用户会以为节点配好了
    var usersReady = App.get('/api/options/users').then(function (list) {
        userOptions = list || [];
    }).catch(function (error) {
        userOptions = [];
        App.toast('审批人列表加载失败：' + error.message, 'err');
    });

    usersReady.then(loadList);
})(window, document);
