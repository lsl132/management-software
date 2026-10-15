/* ==========================================================================
   用户管理页脚本：系统账号增删改查 + 设置角色 + 重置密码 + 冻结/解冻
   约定：
     - 登录名是「登录标识」（user_account.identifier），展示名只是显示用
     - 密码不由页面填写：新用户与重置后统一用系统初始密码，接口不下发明文
     - 不做物理删除，「冻结」替代删除（服务端同时校验禁止冻结当前登录账号）
   ========================================================================== */
(function (window, document) {
    'use strict';

    var App = window.App;

    var STATUS_NORMAL = 1;
    var STATUS_FREEZE = 2;

    var state = {
        current: 1,
        size: 10,
        keyword: '',
        roleId: '',
        status: '',
        total: 0,
        loadedCount: 0,
        editingId: null,
        /** 当前登录用户ID：禁止冻结自己，按钮直接置灰 */
        currentUserId: null,
        /** 正在设置角色的用户 { id, username } */
        assigning: null
    };

    /** 角色下拉的原始选项（页面首次渲染时从筛选下拉缓存，弹窗复用） */
    var roleOptions = null;

    var el = {
        host: document.querySelector('.app-main'),
        keyword: document.getElementById('keyword'),
        filterRole: document.getElementById('filterRole'),
        filterStatus: document.getElementById('filterStatus'),
        btnSearch: document.getElementById('btnSearch'),
        btnReset: document.getElementById('btnReset'),
        btnCreate: document.getElementById('btnCreate'),
        body: document.getElementById('userBody'),
        pagerInfo: document.getElementById('pagerInfo'),
        pageNow: document.getElementById('pageNow'),
        pageSize: document.getElementById('pageSize'),
        btnPrev: document.getElementById('btnPrev'),
        btnNext: document.getElementById('btnNext'),

        modal: document.getElementById('userModal'),
        modalTitle: document.getElementById('userModalTitle'),
        form: document.getElementById('userForm'),
        error: document.getElementById('userError'),
        btnSubmit: document.getElementById('btnSubmitUser'),
        passwordNotice: document.getElementById('passwordNotice'),

        // 表单字段：openModal / 回填都依赖这些引用，漏登记会让弹窗打不开且报错被浏览器静默吞掉
        username: document.getElementById('username'),
        identifier: document.getElementById('identifier'),
        mobile: document.getElementById('mobile'),
        email: document.getElementById('email'),
        roleId: document.getElementById('roleId'),
        status: document.getElementById('status'),

        assignModal: document.getElementById('assignModal'),
        assignTitle: document.getElementById('assignModalTitle'),
        assignForm: document.getElementById('assignForm'),
        assignError: document.getElementById('assignError'),
        assignRoleId: document.getElementById('assignRoleId'),
        btnSubmitAssign: document.getElementById('btnSubmitAssign')
    };

    /* ------------------------------------------------------------ 渲染 */

    function statusBadge(status) {
        var frozen = status === STATUS_FREEZE;
        return '<span class="badge ' + (frozen ? 'badge--muted' : 'badge--ok') + '">'
            + (frozen ? '冻结' : '正常') + '</span>';
    }

    function identifierCell(row) {
        if (!row.identifier) {
            return '<span class="text-muted" title="该用户还没有登录凭证，重置密码会自动补建">未设置</span>';
        }
        return App.dash(row.identifier);
    }

    function actionCell(row) {
        var isSelf = state.currentUserId !== null && Number(row.id) === Number(state.currentUserId);
        var frozen = row.status === STATUS_FREEZE;
        var html = '<div class="table__actions">'
            + '<button type="button" class="btn btn--sm" data-edit="' + row.id + '">编辑</button>'
            + '<button type="button" class="btn btn--sm" data-role="' + row.id + '">设置角色</button>'
            + '<button type="button" class="btn btn--sm" data-reset="' + row.id + '">重置密码</button>';
        if (frozen) {
            html += '<button type="button" class="btn btn--sm" data-unfreeze="' + row.id + '">解冻</button>';
        } else {
            html += '<button type="button" class="btn btn--sm btn--danger" data-freeze="' + row.id + '"'
                + (isSelf ? ' disabled title="不能冻结当前登录的账号"' : '') + '>冻结</button>';
        }
        return html + '</div>';
    }

    function renderRows(records) {
        if (!records || records.length === 0) {
            el.body.innerHTML = '<tr><td colspan="9" class="table__empty">暂无用户数据</td></tr>';
            return;
        }
        el.body.innerHTML = records.map(function (row) {
            return '<tr>'
                + '<td>' + App.dash(row.id) + '</td>'
                + '<td>' + App.dash(row.username) + '</td>'
                + '<td>' + identifierCell(row) + '</td>'
                + '<td>' + App.dash(row.mobile) + '</td>'
                + '<td>' + App.dash(row.email) + '</td>'
                + '<td>' + App.dash(row.roleName) + '</td>'
                + '<td>' + statusBadge(row.status) + '</td>'
                + '<td>' + App.dash(App.formatDateTime(row.createTime)) + '</td>'
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
        if (state.roleId !== '') {
            params.set('roleId', state.roleId);
        }
        if (state.status !== '') {
            params.set('status', state.status);
        }

        el.body.innerHTML = '<tr><td colspan="9" class="table__empty">正在加载…</td></tr>';
        return App.get('/api/users?' + params.toString()).then(function (data) {
            state.total = Number(data.total);
            state.current = Number(data.current);
            state.size = Number(data.size);
            state.loadedCount = data.records ? data.records.length : 0;
            renderRows(data.records);
            renderPager();
        }).catch(function (error) {
            el.body.innerHTML = '<tr><td colspan="9" class="table__empty">加载失败：'
                + App.escapeHtml(error.message) + '</td></tr>';
            App.toast(error.message, 'err');
        });
    }

    /* ------------------------------------------------------------ 用户弹窗 */

    function initRoleOptions() {
        roleOptions = Array.prototype.slice.call(el.filterRole.options)
            .filter(function (option) {
                return option.value !== '';
            })
            .map(function (option) {
                return {value: option.value, label: option.textContent};
            });
    }

    function fillRoleSelect(select, selected) {
        var value = selected === null || selected === undefined ? '' : String(selected);
        var html = '<option value="">未分配角色</option>';
        (roleOptions || []).forEach(function (item) {
            html += '<option value="' + App.escapeHtml(item.value) + '">'
                + App.escapeHtml(item.label) + '</option>';
        });
        select.innerHTML = html;
        select.value = value;
    }

    function openModal(record) {
        state.editingId = record ? record.id : null;
        el.modalTitle.textContent = record ? '编辑用户' : '新增用户';
        el.error.textContent = '';

        fillRoleSelect(el.roleId, record ? record.roleId : '');
        setField('username', record ? record.username : '');
        setField('identifier', record ? record.identifier : '');
        setField('mobile', record ? record.mobile : '');
        setField('email', record ? record.email : '');
        setField('status', record && record.status !== null && record.status !== undefined
            ? record.status : STATUS_NORMAL);

        el.modal.hidden = false;
        el.username.focus();
    }

    function closeModal() {
        el.modal.hidden = true;
        state.editingId = null;
        el.error.textContent = '';
    }

    function submitForm(event) {
        event.preventDefault();

        var payload = {
            username: fieldValue('username'),
            identifier: fieldValue('identifier'),
            mobile: fieldValue('mobile') || null,
            email: fieldValue('email') || null,
            roleId: numberOrNull('roleId'),
            status: numberOrNull('status')
        };
        if (!payload.username) {
            el.error.textContent = '请填写展示名';
            return;
        }
        if (!payload.identifier) {
            el.error.textContent = '请填写登录标识';
            return;
        }

        var editingId = state.editingId;
        el.error.textContent = '';
        el.btnSubmit.disabled = true;

        var pending = editingId
            ? App.put('/api/users/' + editingId, payload)
            : App.post('/api/users', payload);

        pending.then(function () {
            closeModal();
            App.toast(editingId ? '用户修改成功' : '用户创建成功，请用系统初始密码登录', 'ok');
            loadList();
        }).catch(function (error) {
            el.error.textContent = error.message || '保存失败';
        }).then(function () {
            el.btnSubmit.disabled = false;
        });
    }

    /* ------------------------------------------------------------ 行内动作 */

    function rowText(id, column) {
        var button = el.body.querySelector('[data-edit="' + id + '"]');
        var row = button ? button.closest('tr') : null;
        var cell = row ? row.children[column] : null;
        return cell ? cell.textContent.trim() : '';
    }

    function resetPassword(id) {
        var name = rowText(id, 1);
        if (!window.confirm('确认将用户「' + name + '」的密码重置为系统初始密码吗？\n'
                + '重置后旧密码立即失效，请及时告知该用户。')) {
            return;
        }
        App.post('/api/users/' + id + '/password/reset', {}).then(function () {
            App.toast('密码已重置为系统初始密码', 'ok');
            loadList();
        }).catch(function (error) {
            App.toast(error.message || '重置失败', 'err');
        });
    }

    function changeStatus(id, action) {
        var name = rowText(id, 1);
        var confirmText = action === 'freeze'
            ? '确认冻结用户「' + name + '」吗？\n冻结后该账号无法登录（已登录的会话在退出前仍有效）。'
            : '确认解冻用户「' + name + '」吗？解冻后该账号恢复正常登录。';
        if (!window.confirm(confirmText)) {
            return;
        }
        App.post('/api/users/' + id + '/' + action, {}).then(function () {
            App.toast(action === 'freeze' ? '账号已冻结' : '账号已解冻', 'ok');
            loadList();
        }).catch(function (error) {
            App.toast(error.message || '操作失败', 'err');
        });
    }

    /* ------------------------------------------------------------ 角色弹窗 */

    function openAssignModal(id) {
        var name = rowText(id, 1);
        var roleText = rowText(id, 5);
        state.assigning = {id: id, username: name};
        el.assignTitle.textContent = '设置角色 · ' + name;
        el.assignError.textContent = '';
        fillRoleSelect(el.assignRoleId, '');
        // 按列表里显示的角色名回填当前值
        Array.prototype.slice.call(el.assignRoleId.options).forEach(function (option) {
            if (option.textContent === roleText) {
                option.selected = true;
            }
        });
        el.assignModal.hidden = false;
    }

    function closeAssignModal() {
        el.assignModal.hidden = true;
        state.assigning = null;
        el.assignError.textContent = '';
    }

    function submitAssignForm(event) {
        event.preventDefault();
        if (!state.assigning) {
            return;
        }
        el.assignError.textContent = '';
        el.btnSubmitAssign.disabled = true;

        App.post('/api/users/' + state.assigning.id + '/role', {roleId: numberOrNull('assignRoleId')})
            .then(function () {
                closeAssignModal();
                App.toast('角色设置成功', 'ok');
                loadList();
            }).catch(function (error) {
                el.assignError.textContent = error.message || '保存失败';
            }).then(function () {
                el.btnSubmitAssign.disabled = false;
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
            state.roleId = el.filterRole.value;
            state.status = el.filterStatus.value;
            state.current = 1;
            loadList();
        });

        el.btnReset.addEventListener('click', function () {
            el.keyword.value = '';
            el.filterRole.value = '';
            el.filterStatus.value = '';
            state.keyword = '';
            state.roleId = '';
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
                App.get('/api/users/' + editId).then(function (record) {
                    openModal(record);
                }).catch(function (error) {
                    App.toast(error.message, 'err');
                });
                return;
            }

            var roleBtn = target.closest('[data-role]');
            if (roleBtn) {
                openAssignModal(roleBtn.getAttribute('data-role'));
                return;
            }

            var resetBtn = target.closest('[data-reset]');
            if (resetBtn) {
                resetPassword(resetBtn.getAttribute('data-reset'));
                return;
            }

            var freezeBtn = target.closest('[data-freeze]');
            if (freezeBtn) {
                changeStatus(freezeBtn.getAttribute('data-freeze'), 'freeze');
                return;
            }

            var unfreezeBtn = target.closest('[data-unfreeze]');
            if (unfreezeBtn) {
                changeStatus(unfreezeBtn.getAttribute('data-unfreeze'), 'unfreeze');
            }
        });

        el.form.addEventListener('submit', submitForm);
        el.assignForm.addEventListener('submit', submitAssignForm);

        el.modal.addEventListener('click', function (event) {
            if (event.target.closest && event.target.closest('[data-close]')) {
                closeModal();
            }
        });

        el.assignModal.addEventListener('click', function (event) {
            if (event.target.closest && event.target.closest('[data-close]')) {
                closeAssignModal();
            }
        });

        document.addEventListener('keydown', function (event) {
            if (event.key !== 'Escape') {
                return;
            }
            if (!el.assignModal.hidden) {
                closeAssignModal();
                return;
            }
            if (!el.modal.hidden) {
                closeModal();
            }
        });
    }

    state.currentUserId = el.host && el.host.getAttribute('data-current-user-id')
        ? Number(el.host.getAttribute('data-current-user-id')) : null;

    initRoleOptions();
    bindEvents();
    loadList();
})(window, document);
