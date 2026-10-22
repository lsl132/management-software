/* ==========================================================================
   修改密码页脚本：前端校验 -> SM2 加密 -> 提交 -> 会话作废 -> 回登录页
   两段密码合成为一份 JSON 一起加密：SM2 一秘一密，同一个 keyId 只能解一次
   ========================================================================== */
(function (window, document) {
    'use strict';

    var App = window.App;

    /** 与服务端 ProfileService 的规则保持一致；这里先拦一道，少一次往返 */
    var PASSWORD_MIN_LENGTH = 8;
    var PASSWORD_MAX_LENGTH = 32;

    var el = {
        form: document.getElementById('passwordForm'),
        error: document.getElementById('passwordError'),
        btnSubmit: document.getElementById('btnSubmit'),
        oldPassword: document.getElementById('oldPassword'),
        newPassword: document.getElementById('newPassword'),
        confirmPassword: document.getElementById('confirmPassword')
    };

    function showError(message) {
        el.error.textContent = message || '';
    }

    function validate(oldPassword, newPassword, confirmPassword) {
        if (!oldPassword) {
            return '请输入原密码';
        }
        if (!newPassword) {
            return '请输入新密码';
        }
        if (newPassword === oldPassword) {
            return '新密码不能与原密码相同';
        }
        if (newPassword.length < PASSWORD_MIN_LENGTH || newPassword.length > PASSWORD_MAX_LENGTH) {
            return '新密码长度需为 ' + PASSWORD_MIN_LENGTH + '~' + PASSWORD_MAX_LENGTH + ' 位';
        }
        if (!/[A-Za-z]/.test(newPassword) || !/\d/.test(newPassword)) {
            return '新密码需同时包含字母与数字';
        }
        if (newPassword !== confirmPassword) {
            return '两次输入的新密码不一致';
        }
        return null;
    }

    function submit(event) {
        event.preventDefault();

        var oldPassword = el.oldPassword.value;
        var newPassword = el.newPassword.value;
        var confirmPassword = el.confirmPassword.value;

        var problem = validate(oldPassword, newPassword, confirmPassword);
        if (problem) {
            showError(problem);
            return;
        }
        if (!window.sm2) {
            showError('页面未能正常加载，请刷新后重试');
            return;
        }

        showError('');
        el.btnSubmit.disabled = true;
        el.btnSubmit.textContent = '提交中…';

        App.get('/api/auth/public-key').then(function (key) {
            // 明文是 JSON：一段明文、一把公钥、一次解密。拆成两个密文会需要两把公钥，
            // 而同一把公钥第二次解密必然失败（私钥用完即删）
            var payload = JSON.stringify({oldPassword: oldPassword, newPassword: newPassword});
            var cipher = window.sm2.doEncrypt(payload, key.publicKey, 1);
            return App.put('/api/profile/password', {keyId: key.keyId, cipher: cipher});
        }).then(function () {
            // 服务端已作废会话，这里只是把用户送回登录页
            el.form.reset();
            App.toast('密码已修改，请用新密码重新登录', 'ok');
            window.setTimeout(function () {
                window.location.href = '/login';
            }, 900);
        }).catch(function (error) {
            showError(error.message || '修改失败，请重试');
            el.btnSubmit.disabled = false;
            el.btnSubmit.textContent = '确认修改';
        });
    }

    el.form.addEventListener('submit', submit);

    // 原密码框直接给焦点：进来就是为了改密码
    el.oldPassword.focus();
})(window, document);
