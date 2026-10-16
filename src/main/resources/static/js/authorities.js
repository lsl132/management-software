/* ==========================================================================
   权限管理页脚本：权限字典的增删改查，全部 ajax，无整页刷新
   约定：权限为两级结构（模块分组 / 功能页），父子关系由助记符万位推导
        —— 分组助记符为整万位，功能页落在 [分组+1, 分组+9999] 区间内
   ========================================================================== */
(function (window, document) {
    'use strict';

    var App = window.App;

    var TYPE_GROUP = 1;
    var TYPE_PAGE = 2;
    /** 与后端 AuthorityService.GROUP_UNIT 保持一致 */
    var GROUP_UNIT = 10000;

    var state = {
        keyword: '',
        /** 顶层分组（各含 children 功能页），同时用于分组下拉与列表渲染 */
        groups: [],
        editingId: null
    };

    var el = {
        keyword: document.getElementById('keyword'),
        btnSearch: document.getElementById('btnSearch'),
        btnReset: document.getElementById('btnReset'),
        btnCreate: document.getElementById('btnCreate'),
        body: document.getElementById('authorityBody'),
        summary: document.getElementById('listSummary'),

        modal: document.getElementById('authorityModal'),
        modalTitle: document.getElementById('authorityModalTitle'),
        form: document.getElementById('authorityForm'),
        error: document.getElementById('authorityError'),
        type: document.getElementById('type'),
        groupPickField: document.getElementById('groupPickField'),
        groupPick: document.getElementById('groupPick'),
        mnemonic: document.getElementById('mnemonic'),
        mnemonicHint: document.getElementById('mnemonicHint'),
        authName: document.getElementById('authName'),
        pathField: document.getElementById('pathField'),
        path: document.getElementById('path'),
        description: document.getElementById('description'),
        btnSubmit: document.getElementById('btnSubmitAuthority')
    };

    /** 打开弹窗的初始化过程中不触发联动，避免把回填值又算一遍 */
    var applying = false;

    /* ------------------------------------------------------------ 渲染 */

    function typeBadge(type) {
        var isGroup = Number(type) === TYPE_GROUP;
        return '<span class="badge ' + (isGroup ? 'badge--info' : 'badge--ok') + '">'
            + (isGroup ? '模块分组' : '功能页') + '</span>';
    }

    function roleCountCell(record) {
        var count = Number(record.roleCount) || 0;
        if (count === 0) {
            return '<span class="text-muted">—</span>';
        }
        return '<span class="badge badge--info">' + count + ' 个角色</span>';
    }

    /**
     * 功能页路由：直接缀在权限名称下方，不单开一列。
     * 这张表已有 7 列，「说明」一列本来就把表撑得很宽，再加列会让操作列更早被挤出可视区。
     * 没有路由的功能页要显式标出来，否则「菜单里为什么点不动」只能靠猜。
     */
    function pathLine(record) {
        if (Number(record.type) === TYPE_GROUP) {
            return '';
        }
        if (!record.path) {
            return '<span class="auth-row__path auth-row__path--off">未设路由 · 导航置灰</span>';
        }
        return '<span class="auth-row__path">' + App.escapeHtml(record.path) + '</span>';
    }

    function actions(record) {
        var html = '<div class="table__actions">';
        if (Number(record.type) === TYPE_GROUP) {
            html += '<button type="button" class="btn btn--sm" data-add-child="'
                + record.mnemonic + '">加功能页</button>';
        }
        html += '<button type="button" class="btn btn--sm" data-edit="' + record.id + '">编辑</button>'
            + '<button type="button" class="btn btn--sm btn--danger" data-remove="' + record.id
            + '">删除</button>'
            + '</div>';
        return html;
    }

    function renderRows(groups) {
        if (!groups || groups.length === 0) {
            el.body.innerHTML = '<tr><td colspan="7" class="table__empty">没有匹配的权限数据</td></tr>';
            return;
        }

        var html = '';
        groups.forEach(function (group) {
            var children = group.children || [];
            html += '<tr class="auth-row auth-row--group">'
                + '<td><span class="auth-row__name">' + App.dash(group.authName) + '</span></td>'
                + '<td>' + App.dash(group.mnemonic) + '</td>'
                + '<td>' + typeBadge(group.type) + '</td>'
                + '<td>' + App.dash(group.description) + '</td>'
                + '<td>' + children.length + '</td>'
                + '<td>' + roleCountCell(group) + '</td>'
                + '<td>' + actions(group) + '</td>'
                + '</tr>';

            children.forEach(function (page) {
                html += '<tr class="auth-row auth-row--page">'
                    + '<td><span class="auth-row__child">' + App.dash(page.authName) + '</span>'
                    + pathLine(page) + '</td>'
                    + '<td>' + App.dash(page.mnemonic) + '</td>'
                    + '<td>' + typeBadge(page.type) + '</td>'
                    + '<td>' + App.dash(page.description) + '</td>'
                    + '<td><span class="text-muted">—</span></td>'
                    + '<td>' + roleCountCell(page) + '</td>'
                    + '<td>' + actions(page) + '</td>'
                    + '</tr>';
            });
        });
        el.body.innerHTML = html;

        var pageCount = groups.reduce(function (sum, group) {
            return sum + (group.children ? group.children.length : 0);
        }, 0);
        el.summary.textContent = '共 ' + groups.length + ' 个模块分组、' + pageCount + ' 个功能页';
    }

    function loadList() {
        var url = '/api/authorities';
        if (state.keyword) {
            url += '?keyword=' + encodeURIComponent(state.keyword);
        }
        el.body.innerHTML = '<tr><td colspan="7" class="table__empty">正在加载…</td></tr>';
        return App.get(url).then(function (data) {
            state.groups = data || [];
            renderRows(state.groups);
        }).catch(function (error) {
            el.body.innerHTML = '<tr><td colspan="7" class="table__empty">加载失败：'
                + App.escapeHtml(error.message) + '</td></tr>';
            App.toast(error.message, 'err');
        });
    }

    /* ------------------------------------------------------------ 弹窗 */

    function isPageType() {
        return Number(el.type.value) === TYPE_PAGE;
    }

    function currentGroups() {
        return state.groups.filter(function (group) {
            return Number(group.type) === TYPE_GROUP;
        });
    }

    function findGroup(mnemonic) {
        var found = null;
        currentGroups().forEach(function (group) {
            if (Number(group.mnemonic) === Number(mnemonic)) {
                found = group;
            }
        });
        return found;
    }

    /** 分组助记符 → 下一个可用助记符（该分组下最大功能页 +1）。 */
    function nextMnemonicOf(groupMnemonic) {
        var group = findGroup(groupMnemonic);
        var max = 0;
        ((group && group.children) || []).forEach(function (page) {
            var suffix = Number(page.mnemonic) % GROUP_UNIT;
            if (suffix > max) {
                max = suffix;
            }
        });
        return Number(groupMnemonic) + max + 1;
    }

    function renderGroupOptions(selected) {
        el.groupPick.innerHTML = currentGroups().map(function (group) {
            return '<option value="' + group.mnemonic + '">' + App.escapeHtml(group.mnemonic
                + ' ' + group.authName) + '</option>';
        }).join('');
        if (selected !== undefined && selected !== null && findGroup(selected)) {
            el.groupPick.value = String(selected);
        }
    }

    /** 按助记符的万位选中所属分组；下拉里没有对应分组时保持不动（提交时给明确报错）。 */
    function syncGroupPickFromMnemonic() {
        var mnemonic = Number(el.mnemonic.value);
        if (!mnemonic || mnemonic % GROUP_UNIT === 0) {
            return;
        }
        var groupMnemonic = Math.floor(mnemonic / GROUP_UNIT) * GROUP_UNIT;
        if (findGroup(groupMnemonic)) {
            el.groupPick.value = String(groupMnemonic);
        }
    }

    function syncTypeUi() {
        var isPage = isPageType();
        el.groupPickField.hidden = !isPage;
        el.pathField.hidden = !isPage;
        el.mnemonicHint.textContent = isPage
            ? '在所属分组的万位内递增，如 ' + (Number(el.groupPick.value) + 5)
            : '整万位，如 60000';
        if (!isPage) {
            // 分组是下拉容器、没有页面：切过去就把残留的路由清掉，
            // 否则会带着上一个功能页的地址提交，服务端虽然会强制置空，但界面上先给出正确状态更清楚
            el.path.value = '';
            return;
        }
        renderGroupOptions(el.groupPick.value);
        syncGroupPickFromMnemonic();
    }

    function openModal(record, presetGroupMnemonic) {
        state.editingId = record ? record.id : null;
        el.modalTitle.textContent = record ? '编辑权限' : '新增权限';
        el.error.textContent = '';
        renderGroupOptions();

        applying = true;
        if (record) {
            el.type.value = String(record.type);
            el.mnemonic.value = record.mnemonic === null || record.mnemonic === undefined
                ? '' : String(record.mnemonic);
            el.authName.value = record.authName || '';
            el.path.value = record.path || '';
            el.description.value = record.description || '';
            if (Number(record.type) === TYPE_PAGE) {
                el.groupPick.value = String(Math.floor(Number(record.mnemonic) / GROUP_UNIT) * GROUP_UNIT);
            }
        } else if (presetGroupMnemonic) {
            el.type.value = String(TYPE_PAGE);
            el.groupPick.value = String(presetGroupMnemonic);
            el.mnemonic.value = String(nextMnemonicOf(presetGroupMnemonic));
            el.authName.value = '';
            el.path.value = '';
            el.description.value = '';
        } else {
            el.type.value = String(TYPE_GROUP);
            el.mnemonic.value = '';
            el.authName.value = '';
            el.path.value = '';
            el.description.value = '';
        }
        applying = false;

        syncTypeUi();
        // 功能页但助记符为空 / 为整万位（如从分组切过来）→ 给一个落在选中分组里的建议值
        if (isPageType() && !el.mnemonic.value) {
            el.mnemonic.value = String(nextMnemonicOf(el.groupPick.value));
        }

        el.modal.hidden = false;
        el.authName.focus();
    }

    function closeModal() {
        el.modal.hidden = true;
        state.editingId = null;
        el.error.textContent = '';
    }

    function validate() {
        var mnemonic = Number(el.mnemonic.value);
        if (!el.authName.value.trim()) {
            return '请填写权限名称';
        }
        if (!el.mnemonic.value || !Number.isInteger(mnemonic) || mnemonic <= 0) {
            return '请填写权限助记符（正整数）';
        }
        if (isPageType()) {
            if (mnemonic % GROUP_UNIT === 0) {
                return '功能页的助记符不能是整万位，请填 ' + (el.groupPick.value + '1') + ' 这样的值';
            }
            var groupMnemonic = Math.floor(mnemonic / GROUP_UNIT) * GROUP_UNIT;
            if (!findGroup(groupMnemonic)) {
                return '助记符 ' + mnemonic + ' 的万位分组 ' + groupMnemonic
                    + ' 不存在，请先创建这个模块分组';
            }
        } else if (mnemonic % GROUP_UNIT !== 0) {
            return '模块分组的助记符必须是整万位，如 10000、60000';
        }
        if (!isPageType()) {
            return null;
        }
        // 路由可空（代表该功能页还没做），填了就必须是站内绝对路径：
        // 相对路径会跟着当前页 URL 变，同一个菜单项在不同页面点开跳到不同地方
        var path = el.path.value.trim();
        if (!path) {
            return null;
        }
        if (path.charAt(0) !== '/') {
            return '页面路由必须以 / 开头，如 /employees';
        }
        if (/\s/.test(path)) {
            return '页面路由不能包含空格';
        }
        if (path.length > 128) {
            return '页面路由长度不能超过 128';
        }
        return null;
    }

    function submitForm(event) {
        event.preventDefault();

        var message = validate();
        if (message) {
            el.error.textContent = message;
            return;
        }

        var payload = {
            mnemonic: Number(el.mnemonic.value),
            authName: el.authName.value.trim(),
            description: el.description.value.trim() || null,
            type: Number(el.type.value),
            // 分组没有页面，显式送 null；服务端也会强制置空，这里只是别把界面上残留的值带过去
            path: isPageType() ? (el.path.value.trim() || null) : null
        };

        var editingId = state.editingId;
        el.error.textContent = '';
        el.btnSubmit.disabled = true;

        var pending = editingId
            ? App.put('/api/authorities/' + editingId, payload)
            : App.post('/api/authorities', payload);

        pending.then(function () {
            closeModal();
            App.toast(editingId ? '权限修改成功' : '权限创建成功', 'ok');
            loadList();
        }).catch(function (error) {
            el.error.textContent = error.message || '保存失败';
        }).then(function () {
            el.btnSubmit.disabled = false;
        });
    }

    /* ------------------------------------------------------------ 删除 */

    function removeRecord(record) {
        var name = record.authName || '该权限';
        // 本地先看依赖，避免明知会失败还发一次请求；服务端仍是最终权威
        var childCount = record.children ? record.children.length : 0;
        if (Number(record.type) === TYPE_GROUP && childCount > 0) {
            window.alert('无法删除「' + name + '」：该模块分组下还有 ' + childCount
                + ' 个功能页，请先删除或调整这些功能页。');
            return;
        }
        var roleCount = Number(record.roleCount) || 0;
        if (roleCount > 0) {
            window.alert('无法删除「' + name + '」：该权限已被 ' + roleCount
                + ' 个角色引用，请先在角色管理中解除授权。');
            return;
        }
        if (!window.confirm('确认删除权限「' + name + '」吗？删除后不可恢复。')) {
            return;
        }
        App.del('/api/authorities/' + record.id).then(function () {
            App.toast('权限删除成功', 'ok');
            loadList();
        }).catch(function (error) {
            App.toast(error.message || '删除失败', 'err');
        });
    }

    /** 从列表数据里按主键找到某一行（分组或其子页），用于删除时的依赖判断提示。 */
    function findRecord(id) {
        var target = null;
        state.groups.forEach(function (group) {
            if (Number(group.id) === Number(id)) {
                target = group;
            }
            (group.children || []).forEach(function (page) {
                if (Number(page.id) === Number(id)) {
                    target = page;
                }
            });
        });
        return target;
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

        el.type.addEventListener('change', function () {
            if (applying) {
                return;
            }
            syncTypeUi();
            if (isPageType() && !el.mnemonic.value) {
                el.mnemonic.value = String(nextMnemonicOf(el.groupPick.value));
            }
        });

        el.groupPick.addEventListener('change', function () {
            if (applying) {
                return;
            }
            syncTypeUi();
            el.mnemonic.value = String(nextMnemonicOf(el.groupPick.value));
        });

        el.mnemonic.addEventListener('input', function () {
            if (applying || !isPageType()) {
                return;
            }
            syncGroupPickFromMnemonic();
        });

        el.body.addEventListener('click', function (event) {
            var target = event.target;
            if (!target.closest) {
                return;
            }

            var addChild = target.closest('[data-add-child]');
            if (addChild) {
                openModal(null, addChild.getAttribute('data-add-child'));
                return;
            }

            var editBtn = target.closest('[data-edit]');
            if (editBtn) {
                var editId = editBtn.getAttribute('data-edit');
                App.get('/api/authorities/' + editId).then(function (record) {
                    openModal(record);
                }).catch(function (error) {
                    App.toast(error.message, 'err');
                });
                return;
            }

            var removeBtn = target.closest('[data-remove]');
            if (removeBtn) {
                var record = findRecord(removeBtn.getAttribute('data-remove'));
                if (record) {
                    removeRecord(record);
                }
            }
        });

        el.form.addEventListener('submit', submitForm);

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

    bindEvents();
    loadList();
})(window, document);
