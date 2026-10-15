/* ==========================================================================
   角色管理页脚本：角色增删改查 + 挂载权限（两级权限树，父子联动）
   约定：
     - 权限树来自 /api/authorities（模块分组 → 功能页），一次加载后缓存
     - 挂载按助记符全量覆盖：PUT /api/roles/{id}/authorities
     - 勾选模块分组自动全选其下功能页；功能页全部取消时分组自动取消；
       只勾部分功能页时分组呈半选（保存时一并提交分组助记符，保证菜单可见）
   ========================================================================== */
(function (window, document) {
    'use strict';

    var App = window.App;

    var state = {
        keyword: '',
        editingId: null,
        /** 当前挂载权限的角色 { id, name } */
        currentRole: null,
        /** 权限树缓存 */
        tree: null
    };

    var el = {
        keyword: document.getElementById('keyword'),
        btnSearch: document.getElementById('btnSearch'),
        btnReset: document.getElementById('btnReset'),
        btnCreate: document.getElementById('btnCreate'),
        body: document.getElementById('roleBody'),
        listSummary: document.getElementById('listSummary'),

        modal: document.getElementById('roleModal'),
        modalTitle: document.getElementById('roleModalTitle'),
        form: document.getElementById('roleForm'),
        error: document.getElementById('roleError'),
        btnSubmit: document.getElementById('btnSubmitRole'),

        permModal: document.getElementById('permModal'),
        permTitle: document.getElementById('permModalTitle'),
        permError: document.getElementById('permError'),
        permHint: document.getElementById('permHint'),
        authTree: document.getElementById('authTree'),
        btnCheckAll: document.getElementById('btnCheckAll'),
        btnClearAll: document.getElementById('btnClearAll'),
        btnSavePerm: document.getElementById('btnSavePerm')
    };

    /* ------------------------------------------------------------ 角色列表 */

    function statusBadge(status) {
        return status === 2
            ? '<span class="badge badge--muted">停用</span>'
            : '<span class="badge badge--ok">启用</span>';
    }

    function countCell(value) {
        var count = value === null || value === undefined ? 0 : Number(value);
        return count > 0
            ? '<span class="badge badge--info">' + count + '</span>'
            : '<span class="text-muted">0</span>';
    }

    function renderRows(records) {
        if (!records || records.length === 0) {
            el.body.innerHTML = '<tr><td colspan="8" class="table__empty">暂无角色数据</td></tr>';
            el.listSummary.textContent = '';
            return;
        }
        el.body.innerHTML = records.map(function (row) {
            return '<tr>'
                + '<td>' + App.dash(row.id) + '</td>'
                + '<td>' + App.dash(row.name) + '</td>'
                + '<td>' + App.dash(row.description) + '</td>'
                + '<td>' + statusBadge(row.status) + '</td>'
                + '<td>' + countCell(row.userCount) + '</td>'
                + '<td>' + countCell(row.authorityCount) + '</td>'
                + '<td>' + App.dash(App.formatDateTime(row.createTime)) + '</td>'
                + '<td><div class="table__actions">'
                + '<button type="button" class="btn btn--sm" data-edit="' + row.id + '">编辑</button>'
                + '<button type="button" class="btn btn--sm" data-perm="' + row.id + '">挂载权限</button>'
                + '<button type="button" class="btn btn--sm btn--danger" data-remove="' + row.id + '">删除</button>'
                + '</div></td>'
                + '</tr>';
        }).join('');
        el.listSummary.textContent = '共 ' + records.length + ' 个角色';
    }

    function loadList() {
        var url = '/api/roles';
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

    /* ------------------------------------------------------------ 角色弹窗 */

    function openModal(record) {
        state.editingId = record ? record.id : null;
        el.modalTitle.textContent = record ? '编辑角色' : '新增角色';
        el.error.textContent = '';

        setField('name', record ? record.name : '');
        setField('description', record ? record.description : '');
        setField('status', record && record.status !== null && record.status !== undefined ? record.status : 1);

        el.modal.hidden = false;
        document.getElementById('name').focus();
    }

    function closeModal() {
        el.modal.hidden = true;
        state.editingId = null;
        el.error.textContent = '';
    }

    function submitForm(event) {
        event.preventDefault();

        var payload = {
            name: fieldValue('name'),
            description: fieldValue('description') || null,
            status: numberOrNull('status')
        };
        if (!payload.name) {
            el.error.textContent = '请填写角色名称';
            return;
        }

        var editingId = state.editingId;
        el.error.textContent = '';
        el.btnSubmit.disabled = true;

        var pending = editingId
            ? App.put('/api/roles/' + editingId, payload)
            : App.post('/api/roles', payload);

        pending.then(function () {
            closeModal();
            App.toast(editingId ? '角色修改成功' : '角色新增成功', 'ok');
            loadList();
        }).catch(function (error) {
            el.error.textContent = error.message || '保存失败';
        }).then(function () {
            el.btnSubmit.disabled = false;
        });
    }

    function removeRole(id, name) {
        if (!window.confirm('确认删除角色「' + name + '」吗？\n'
                + '若仍有用户使用该角色，删除会被拒绝；删除后该角色的权限挂载记录一并清除。')) {
            return;
        }
        App.del('/api/roles/' + id).then(function () {
            App.toast('角色删除成功', 'ok');
            loadList();
        }).catch(function (error) {
            App.toast(error.message || '删除失败', 'err');
        });
    }

    /* ------------------------------------------------------------ 权限弹窗 */

    function loadTree() {
        if (state.tree) {
            return Promise.resolve(state.tree);
        }
        return App.get('/api/authorities').then(function (tree) {
            state.tree = tree || [];
            return state.tree;
        });
    }

    function renderTree(tree, checkedMnemonics) {
        var checked = {};
        (checkedMnemonics || []).forEach(function (mnemonic) {
            checked[mnemonic] = true;
        });

        if (!tree || tree.length === 0) {
            el.authTree.innerHTML = '<p class="empty-hint">暂无权限数据，请先执行 docs/db/seed-authority.sql</p>';
            return;
        }

        el.authTree.innerHTML = tree.map(function (group) {
            var groupChecked = checked[group.mnemonic] === true;
            var children = group.children || [];
            var items = children.map(function (page) {
                var pageChecked = checked[page.mnemonic] === true;
                return '<label class="auth-item">'
                    + '<input type="checkbox" data-page="' + page.mnemonic + '" data-group="'
                    + group.mnemonic + '"' + (pageChecked ? ' checked' : '') + '/>'
                    + '<span class="auth-item__name">' + App.escapeHtml(page.authName) + '</span>'
                    + '<span class="auth-item__code">' + page.mnemonic + '</span>'
                    + '</label>';
            }).join('');

            // 勾了分组而子项没勾 → 视为整组授权，界面上把子项也勾上（与服务端归一化一致）
            if (groupChecked && !children.some(function (page) {
                return checked[page.mnemonic] === true;
            })) {
                items = children.map(function (page) {
                    return '<label class="auth-item">'
                        + '<input type="checkbox" data-page="' + page.mnemonic + '" data-group="'
                        + group.mnemonic + '" checked/>'
                        + '<span class="auth-item__name">' + App.escapeHtml(page.authName) + '</span>'
                        + '<span class="auth-item__code">' + page.mnemonic + '</span>'
                        + '</label>';
                }).join('');
            }

            return '<section class="auth-group">'
                + '<label class="auth-group__head">'
                + '<input type="checkbox" class="auth-group__toggle" data-group="' + group.mnemonic + '"'
                + (groupChecked ? ' checked' : '') + '/>'
                + '<span class="auth-group__name">' + App.escapeHtml(group.authName) + '</span>'
                + '<span class="auth-group__code">' + group.mnemonic + '</span>'
                + '<span class="auth-group__meta">' + children.length + ' 个功能页</span>'
                + '</label>'
                + '<div class="auth-group__items">' + items + '</div>'
                + '</section>';
        }).join('');

        syncGroupStates();
        updateHint();
    }

    function groupToggles() {
        return Array.prototype.slice.call(el.authTree.querySelectorAll('.auth-group__toggle'));
    }

    function pageBoxesOf(groupMnemonic) {
        return Array.prototype.slice.call(
            el.authTree.querySelectorAll('[data-page][data-group="' + groupMnemonic + '"]'));
    }

    /** 按子项勾选情况刷新分组框：全勾=勾选、部分=半选、全不勾=取消。 */
    function syncGroupStates() {
        groupToggles().forEach(function (toggle) {
            var boxes = pageBoxesOf(toggle.getAttribute('data-group'));
            if (boxes.length === 0) {
                return;
            }
            var checkedCount = boxes.filter(function (box) {
                return box.checked;
            }).length;
            toggle.checked = checkedCount > 0;
            toggle.indeterminate = checkedCount > 0 && checkedCount < boxes.length;
        });
    }

    /** 收集待提交的助记符：勾选的（或半选的）分组 + 勾选的功能页。 */
    function collectSelected() {
        var selected = [];
        groupToggles().forEach(function (toggle) {
            if (toggle.checked || toggle.indeterminate) {
                selected.push(Number(toggle.getAttribute('data-group')));
            }
        });
        Array.prototype.slice.call(el.authTree.querySelectorAll('[data-page]')).forEach(function (box) {
            if (box.checked) {
                selected.push(Number(box.getAttribute('data-page')));
            }
        });
        return selected;
    }

    function updateHint() {
        var selected = collectSelected();
        var pages = selected.length;
        el.permHint.textContent = pages === 0
            ? '未选择任何权限（当前角色将没有任何菜单）'
            : '已选 ' + pages + ' 项权限（含分组）';
    }

    function openPermModal(id, name) {
        state.currentRole = {id: id, name: name};
        el.permTitle.textContent = '挂载权限 · ' + name;
        el.permError.textContent = '';
        el.permHint.textContent = '正在加载权限树…';
        el.authTree.innerHTML = '';
        el.permModal.hidden = false;

        Promise.all([loadTree(), App.get('/api/roles/' + id + '/authorities')])
            .then(function (results) {
                renderTree(results[0], results[1]);
            })
            .catch(function (error) {
                el.permError.textContent = error.message || '权限加载失败';
            });
    }

    function closePermModal() {
        el.permModal.hidden = true;
        state.currentRole = null;
        el.permError.textContent = '';
    }

    function savePermissions() {
        if (!state.currentRole) {
            return;
        }
        el.permError.textContent = '';
        el.btnSavePerm.disabled = true;

        App.put('/api/roles/' + state.currentRole.id + '/authorities', {mnemonics: collectSelected()})
            .then(function () {
                closePermModal();
                App.toast('权限保存成功', 'ok');
                loadList();
            }).catch(function (error) {
                el.permError.textContent = error.message || '保存失败';
            }).then(function () {
                el.btnSavePerm.disabled = false;
            });
    }

    /* ------------------------------------------------------------ 工具 */

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

    /* ------------------------------------------------------------ 事件 */

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
            openModal(null);
        });

        el.body.addEventListener('click', function (event) {
            var target = event.target;
            if (!target.closest) {
                return;
            }

            var editBtn = target.closest('[data-edit]');
            if (editBtn) {
                var editId = editBtn.getAttribute('data-edit');
                App.get('/api/roles/' + editId).then(function (record) {
                    openModal(record);
                }).catch(function (error) {
                    App.toast(error.message, 'err');
                });
                return;
            }

            var permBtn = target.closest('[data-perm]');
            if (permBtn) {
                var permRow = permBtn.closest('tr');
                var nameCell = permRow ? permRow.children[1] : null;
                openPermModal(permBtn.getAttribute('data-perm'),
                    nameCell ? nameCell.textContent.trim() : '该角色');
                return;
            }

            var removeBtn = target.closest('[data-remove]');
            if (removeBtn) {
                var removeRow = removeBtn.closest('tr');
                var roleNameCell = removeRow ? removeRow.children[1] : null;
                removeRole(removeBtn.getAttribute('data-remove'),
                    roleNameCell ? roleNameCell.textContent.trim() : '该角色');
            }
        });

        el.form.addEventListener('submit', submitForm);

        el.modal.addEventListener('click', function (event) {
            if (event.target.closest && event.target.closest('[data-close]')) {
                closeModal();
            }
        });

        // 权限树：勾分组=全选其下功能页；勾/取消功能页=刷新分组状态
        el.authTree.addEventListener('change', function (event) {
            var target = event.target;
            if (!target || target.type !== 'checkbox') {
                return;
            }
            if (target.classList.contains('auth-group__toggle')) {
                var groupMnemonic = target.getAttribute('data-group');
                pageBoxesOf(groupMnemonic).forEach(function (box) {
                    box.checked = target.checked;
                });
                target.indeterminate = false;
            } else if (target.hasAttribute('data-page')) {
                syncGroupStates();
            }
            updateHint();
        });

        el.btnCheckAll.addEventListener('click', function () {
            Array.prototype.slice.call(el.authTree.querySelectorAll('input[type="checkbox"]'))
                .forEach(function (box) {
                    box.checked = true;
                    box.indeterminate = false;
                });
            updateHint();
        });

        el.btnClearAll.addEventListener('click', function () {
            Array.prototype.slice.call(el.authTree.querySelectorAll('input[type="checkbox"]'))
                .forEach(function (box) {
                    box.checked = false;
                    box.indeterminate = false;
                });
            updateHint();
        });

        el.btnSavePerm.addEventListener('click', savePermissions);

        el.permModal.addEventListener('click', function (event) {
            if (event.target.closest && event.target.closest('[data-close]')) {
                closePermModal();
            }
        });

        document.addEventListener('keydown', function (event) {
            if (event.key !== 'Escape') {
                return;
            }
            if (!el.permModal.hidden) {
                closePermModal();
                return;
            }
            if (!el.modal.hidden) {
                closeModal();
            }
        });
    }

    bindEvents();
    loadList();
})(window, document);
