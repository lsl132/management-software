/* ==========================================================================
   登录页脚本：取一次性 SM2 公钥 -> 前端加密密码 -> ajax 提交
   勾选「记住登录账号」时，仅把账号名写入 localStorage（不保存任何密码痕迹）
   ========================================================================== */
(function (window, document) {
    'use strict';

    /** localStorage 键：记住的登录账号，值即账号名 */
    var REMEMBER_KEY = 'backend:login:remember-username';

    var form = document.getElementById('loginForm');
    var errorBox = document.getElementById('loginError');
    var submitBtn = document.getElementById('loginSubmit');
    var usernameInput = document.getElementById('username');
    var passwordInput = document.getElementById('password');
    var rememberInput = document.getElementById('rememberMe');

    /* localStorage 在隐私模式或被浏览器禁用时会直接抛异常，统一兜底为「未记住」，不影响登录 */

    function readRemembered() {
        try {
            return window.localStorage.getItem(REMEMBER_KEY) || '';
        } catch (e) {
            return '';
        }
    }

    function saveRemembered(value) {
        try {
            window.localStorage.setItem(REMEMBER_KEY, value);
        } catch (e) {
            // 存储不可用时静默忽略
        }
    }

    function clearRemembered() {
        try {
            window.localStorage.removeItem(REMEMBER_KEY);
        } catch (e) {
            // 存储不可用时静默忽略
        }
    }

    /** 回填上次记住的账号：勾选框与账号输入框同步；不覆盖浏览器已自动填充的内容 */
    function applyRemembered() {
        var remembered = readRemembered();
        if (!remembered) {
            return;
        }
        if (rememberInput) {
            rememberInput.checked = true;
        }
        if (usernameInput && !usernameInput.value) {
            usernameInput.value = remembered;
        }
    }

    /** 登录成功后按勾选状态保存或清除账号 */
    function persistRemembered(username) {
        if (rememberInput && rememberInput.checked) {
            saveRemembered(username);
        } else {
            clearRemembered();
        }
    }

    function showError(message) {
        errorBox.textContent = message || '';
    }

    function setLoading(loading) {
        submitBtn.disabled = loading;
        submitBtn.textContent = loading ? '登录中…' : '登 录';
    }

    function submit(event) {
        event.preventDefault();

        var username = usernameInput.value.trim();
        var password = passwordInput.value;
        if (!username) {
            showError('请输入登录账号');
            usernameInput.focus();
            return;
        }
        if (!password) {
            showError('请输入登录密码');
            passwordInput.focus();
            return;
        }
        if (!window.sm2) {
            showError('页面未能正常加载，请刷新后重试');
            return;
        }

        showError('');
        setLoading(true);

        // 1) 取一次性公钥（私钥在服务端 Redis，一次一密）
        window.App.get('/api/auth/public-key')
            .then(function (key) {
                // 2) 用 SM2 公钥加密密码，cipherMode = 1（C1C3C2），与服务端解密实现对应
                var cipher = window.sm2.doEncrypt(password, key.publicKey, 1);
                // 3) 只提交密文，不提交明文
                return window.App.post('/api/auth/login', {
                    username: username,
                    keyId: key.keyId,
                    cipher: cipher
                });
            })
            .then(function (result) {
                persistRemembered(username);
                passwordInput.value = '';
                window.location.href = (result && result.redirect) ? result.redirect : '/home';
            })
            .catch(function (error) {
                showError(error.message || '登录失败，请重试');
                setLoading(false);
            });
    }

    if (form) {
        form.addEventListener('submit', submit);
        applyRemembered();
        if (usernameInput && usernameInput.value && passwordInput) {
            passwordInput.focus();
        } else if (usernameInput) {
            usernameInput.focus();
        }
    }
})(window, document);
