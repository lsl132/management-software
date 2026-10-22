/* ==========================================================================
   个人信息页脚本：读取本人资料 + 保存显示名/手机/邮箱
   只读项（登录账号、角色、状态）由服务端下发，页面不提供修改入口
   ========================================================================== */
(function (window, document) {
    'use strict';

    var App = window.App;

    var el = {
        form: document.getElementById('profileForm'),
        error: document.getElementById('profileError'),
        btnSave: document.getElementById('btnSave'),
        btnReset: document.getElementById('btnReset'),
        identifier: document.getElementById('identifier'),
        roleName: document.getElementById('roleName'),
        statusText: document.getElementById('statusText'),
        createTime: document.getElementById('createTime'),
        lastLogin: document.getElementById('lastLogin'),
        username: document.getElementById('username'),
        mobile: document.getElementById('mobile'),
        email: document.getElementById('email')
    };

    /** 最近一次从服务端读到的资料，供「还原」回填，避免为了撤销一次输入再请求一遍 */
    var loaded = null;

    function setStatic(node, value, emptyText) {
        node.textContent = value === null || value === undefined || value === ''
            ? (emptyText || '—')
            : String(value);
    }

    function render(record) {
        loaded = record;

        setStatic(el.identifier, record.identifier);
        setStatic(el.roleName, record.roleName, '未分配角色');
        setStatic(el.statusText, record.statusText);
        setStatic(el.createTime, App.formatDateTime(record.createTime));
        setStatic(el.lastLogin, record.lastLoginTime
            ? App.formatDateTime(record.lastLoginTime)
                + (record.lastLoginIp ? ' · ' + record.lastLoginIp : '')
            : '', '从未登录');

        el.username.value = record.username || '';
        el.mobile.value = record.mobile || '';
        el.email.value = record.email || '';
    }

    function load() {
        App.get('/api/profile').then(render).catch(function (error) {
            el.error.textContent = error.message || '资料加载失败';
        });
    }

    /**
     * 保存成功后同步顶栏。
     *
     * <p>服务端已把新显示名写回会话，但页面上的顶栏是**上次渲染**的结果，不刷就还是旧名字。
     * 不整页刷新是为了保留「保存后留在原处」的体验。
     */
    function syncHeader(username) {
        var nameNode = document.querySelector('.user-box__name');
        if (nameNode) {
            nameNode.textContent = username;
        }
        var avatar = document.querySelector('.user-box__avatar');
        if (avatar && username) {
            avatar.textContent = username.substring(0, 1).toUpperCase();
        }
    }

    function submit(event) {
        event.preventDefault();

        var username = el.username.value.trim();
        if (!username) {
            el.error.textContent = '请填写显示名';
            el.username.focus();
            return;
        }
        var email = el.email.value.trim();
        if (email && email.indexOf('@') < 1) {
            el.error.textContent = '电子邮箱格式不正确';
            el.email.focus();
            return;
        }

        el.error.textContent = '';
        el.btnSave.disabled = true;

        App.put('/api/profile', {
            username: username,
            // 空串提交成 null：服务端按「清空」处理，不会写成空字符串
            mobile: el.mobile.value.trim() || null,
            email: email || null
        }).then(function (record) {
            render(record);
            syncHeader(record.username);
            App.toast('个人信息已保存', 'ok');
        }).catch(function (error) {
            el.error.textContent = error.message || '保存失败';
        }).then(function () {
            el.btnSave.disabled = false;
        });
    }

    function reset() {
        el.error.textContent = '';
        if (loaded) {
            render(loaded);
        }
    }

    el.form.addEventListener('submit', submit);
    el.btnReset.addEventListener('click', reset);

    load();
})(window, document);
