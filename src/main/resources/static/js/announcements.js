/* ==========================================================================
   公告管理页脚本：公告增删改查 + 发布/下线，全部 ajax，无整页刷新
   约定：作者由服务端按登录用户写入；发布时间只在首次发布时确定
   正文为富文本：编辑用 Quill（仅开放「精简」排版），存库前服务端还会用 jsoup 再清洗一遍，
   前端渲染时用 DOMPurify 按同一份白名单清洗——三处白名单必须保持一致
   ========================================================================== */
(function (window, document) {
    'use strict';

    var App = window.App;

    var STATUS_DRAFT = 1;
    var STATUS_PUBLISHED = 2;
    var STATUS_OFFLINE = 3;

    /** 正文允许的最大纯文字长度（不含排版标记），与服务端 RichTextUtils 的校验保持一致。 */
    var MAX_CONTENT_TEXT_LENGTH = 20000;

    /** 富文本白名单：编辑器工具栏、前端 DOMPurify、服务端 jsoup 三处必须一致。 */
    var ALLOWED_TAGS = ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 'ol', 'ul', 'li'];
    /** 出现这些标签就按富文本渲染；否则视为历史纯文本公告，按原文保留换行。 */
    var RICH_BLOCK_TAGS = /<(p|br|div|ul|ol|li|h[1-6]|blockquote|strong|b|em|i|u|s)\b/i;

    /** Quill 实例，页面初始化时创建。 */
    var quill = null;

    var state = {
        current: 1,
        size: 10,
        keyword: '',
        status: '',
        total: 0,
        loadedCount: 0,
        editingId: null,
        /** 本次提交要附带的状态：草稿/发布由按钮决定，编辑时为 null（不变更状态） */
        submitStatus: null,
        /** 当前详情弹窗展示的那条公告，供「详情 → 编辑」直接复用，避免再请求一次 */
        detailRecord: null
    };

    var el = {
        keyword: document.getElementById('keyword'),
        filterStatus: document.getElementById('filterStatus'),
        btnSearch: document.getElementById('btnSearch'),
        btnReset: document.getElementById('btnReset'),
        btnCreate: document.getElementById('btnCreate'),
        body: document.getElementById('announcementBody'),
        pagerInfo: document.getElementById('pagerInfo'),
        pageNow: document.getElementById('pageNow'),
        pageSize: document.getElementById('pageSize'),
        btnPrev: document.getElementById('btnPrev'),
        btnNext: document.getElementById('btnNext'),

        modal: document.getElementById('announcementModal'),
        modalTitle: document.getElementById('announcementModalTitle'),
        form: document.getElementById('announcementForm'),
        error: document.getElementById('announcementError'),
        title: document.getElementById('title'),
        contentEditor: document.getElementById('contentEditor'),
        attachment: document.getElementById('attachment'),
        btnSaveDraft: document.getElementById('btnSaveDraft'),
        btnSavePublish: document.getElementById('btnSavePublish'),
        btnSaveEdit: document.getElementById('btnSaveEdit'),

        detailModal: document.getElementById('announcementDetailModal'),
        detailTitle: document.getElementById('announcementDetailTitle'),
        detailMeta: document.getElementById('announcementDetailMeta'),
        detailContent: document.getElementById('announcementDetailContent'),
        detailAttachment: document.getElementById('announcementDetailAttachment'),
        btnDetailEdit: document.getElementById('btnDetailEdit')
    };

    /* ------------------------------------------------------------ 富文本编辑器 */

    /**
     * 初始化 Quill：只开放「加粗 / 斜体 / 下划线 / 有序·无序列表」四种排版。
     *
     * <p>{@code formats} 与工具栏同时收窄——工具栏只决定按钮，{@code formats} 才决定
     * 「粘贴进来的内容能保留多少格式」，两者都收窄才能保证只产出白名单内的标签。
     */
    function initEditor() {
        if (!el.contentEditor || !window.Quill) {
            return;
        }
        quill = new window.Quill(el.contentEditor, {
            theme: 'snow',
            formats: ['bold', 'italic', 'underline', 'list'],
            placeholder: '公告正文…',
            modules: {
                toolbar: [
                    ['bold', 'italic', 'underline'],
                    [{list: 'ordered'}, {list: 'bullet'}]
                ]
            }
        });

        // 不开放媒体：拖入文件直接拦掉，粘贴图片在捕获阶段拦掉（抢在 Quill 之前）
        quill.root.addEventListener('drop', function (event) {
            if (event.dataTransfer && event.dataTransfer.files && event.dataTransfer.files.length) {
                event.preventDefault();
                App.toast('正文暂不支持插入图片或文件，请使用文字排版', 'err');
            }
        });
        quill.root.addEventListener('paste', function (event) {
            var data = event.clipboardData;
            if (data && data.files && data.files.length) {
                event.preventDefault();
                event.stopPropagation();
                App.toast('正文暂不支持粘贴图片，请使用文字排版', 'err');
            }
        }, true);
    }

    /** 取正文（语义化 HTML）。只剩空行时归一为空串，避免存进 `<p><br></p>` 这类空壳。 */
    function getEditorContent() {
        if (!quill || quill.getText().trim() === '') {
            return '';
        }
        return quill.getSemanticHTML();
    }

    /**
     * 历史纯文本正文 → HTML。
     *
     * <p>库里早期公告存的是纯文本，且**都带换行**（实测 50 条全部命中）。若直接
     * `dangerouslyPasteHTML`，HTML 里的 `\n` 只是空白字符，换行会被压成空格、整篇挤成一段。
     *
     * <p>**逐行转成一个 `<p>`**，而不是「段内用 `<br>`、空行分段」：Quill 的文档模型里
     * 「一行」本来就是一个块（回车即新建段落），实测把 `<br>` 粘进去它也会被规范化成
     * 独立段落。按它的模型来写，形态才稳定——编辑一次再打开不会漂移，也不会出现
     * 「本以为是一段、渲染成两段」的错位。空行转 `<p><br></p>` 把空行一并留住，
     * 与详情页 `white-space: pre-wrap` 的观感对齐。
     */
    function plainTextToHtml(text) {
        var normalized = String(text).replace(/\r\n?/g, '\n').replace(/\n+$/, '');
        if (!normalized) {
            return '';
        }
        return normalized
            .split('\n')
            .map(function (line) {
                return line === '' ? '<p><br></p>' : '<p>' + App.escapeHtml(line) + '</p>';
            })
            .join('');
    }

    /**
     * 回填正文。
     *
     * <p>按形态分流：本就是富文本 HTML 的原样清洗后灌入；历史纯文本先按换行还原成段落。
     * 无论哪种，编辑一次之后保存回去就统一成了富文本。
     */
    function setEditorContent(html) {
        if (!quill) {
            return;
        }
        quill.setText('');
        var value = html === null || html === undefined ? '' : String(html);
        if (!value.trim()) {
            return;
        }
        quill.clipboard.dangerouslyPasteHTML(sanitizeRich(RICH_BLOCK_TAGS.test(value) ? value : plainTextToHtml(value)));
    }

    /**
     * 按白名单清洗 HTML。
     *
     * <p>DOMPurify 未加载时**降级为纯文本转义**（宁可丢掉排版也不放大 XSS 面）。
     * 服务端还会再清洗一次，这里是渲染前的第一道防线。
     */
    function sanitizeRich(html) {
        var raw = html === null || html === undefined ? '' : String(html);
        if (!window.DOMPurify) {
            window.console.warn('[公告] DOMPurify 未加载，正文按纯文本渲染');
            return App.escapeHtml(raw);
        }
        return window.DOMPurify.sanitize(raw, {ALLOWED_TAGS: ALLOWED_TAGS, ALLOWED_ATTR: []});
    }

    /* ------------------------------------------------------------ 渲染 */

    function statusBadge(status) {
        var text = status === STATUS_DRAFT ? '草稿'
            : status === STATUS_PUBLISHED ? '已发布'
                : status === STATUS_OFFLINE ? '已下线' : '未知';
        var cls = status === STATUS_PUBLISHED ? 'badge--ok'
            : status === STATUS_DRAFT ? 'badge--warn' : 'badge--muted';
        return '<span class="badge ' + cls + '">' + text + '</span>';
    }

    /**
     * 标题列：标题本身可点击打开只读详情，下方是灰色摘要（正文转纯文本后截断，不把标签带出来）。
     *
     * <p>整段装在 `.ann-cell` 里限宽（见 CSS）——否则 40 字摘要会把「标题」列撑到 538px，
     * 把「操作」列挤出可视区。被截断的文案挂在 `title` 上，悬浮可看全。
     */
    function summaryCell(record) {
        var full = App.htmlToText(record.content).replace(/\s+/g, ' ');
        var text = full.length > 40 ? full.slice(0, 40) + '…' : full;
        var title = record.title === null || record.title === undefined ? '' : String(record.title);
        return '<div class="ann-cell">'
            + '<button type="button" class="ann-cell__title" data-view="' + record.id + '"'
            + ' title="查看公告详情：' + App.escapeHtml(title) + '">' + App.dash(record.title) + '</button>'
            + (text
                ? '<span class="ann-cell__summary" title="' + App.escapeHtml(full) + '">'
                    + App.escapeHtml(text) + '</span>'
                : '')
            + '</div>';
    }

    function attachmentCell(record) {
        if (!record.attachment) {
            return '<span class="text-muted">—</span>';
        }
        return '<a href="' + App.escapeHtml(record.attachment) + '" target="_blank" rel="noopener">附件</a>';
    }

    function actionCell(record) {
        var html = '<div class="table__actions">'
            + '<button type="button" class="btn btn--sm" data-view="' + record.id + '">详情</button>'
            + '<button type="button" class="btn btn--sm" data-edit="' + record.id + '">编辑</button>';
        if (record.status === STATUS_PUBLISHED) {
            html += '<button type="button" class="btn btn--sm" data-offline="' + record.id + '">下线</button>';
        } else {
            html += '<button type="button" class="btn btn--sm" data-publish="' + record.id + '">发布</button>';
        }
        html += '<button type="button" class="btn btn--sm btn--danger" data-remove="' + record.id + '">删除</button>'
            + '</div>';
        return html;
    }

    /**
     * 列表里的时间：去掉秒。
     *
     * <p>一行里塞两个含秒的完整时间戳，比不带秒多占约 42px，会把整张表挤到横向滚动、
     * 让「创建时间」被右侧吸附的操作列压掉尾巴。秒在列表里没有参考价值（完整时间戳在
     * 「详情」弹窗里仍能看到），去掉后表格正好收进容器。
     */
    function formatListTime(value) {
        var text = App.formatDateTime(value);
        return text ? text.replace(/(\d{2}:\d{2}):\d{2}$/, '$1') : text;
    }

    function renderRows(records) {
        if (!records || records.length === 0) {
            el.body.innerHTML = '<tr><td colspan="8" class="table__empty">暂无公告数据</td></tr>';
            return;
        }
        el.body.innerHTML = records.map(function (row) {
            return '<tr>'
                + '<td>' + App.dash(row.id) + '</td>'
                + '<td>' + summaryCell(row) + '</td>'
                + '<td>' + statusBadge(row.status) + '</td>'
                + '<td>' + App.dash(row.author) + '</td>'
                + '<td>' + App.dash(formatListTime(row.publishTime)) + '</td>'
                + '<td>' + attachmentCell(row) + '</td>'
                + '<td>' + App.dash(formatListTime(row.createTime)) + '</td>'
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

        el.body.innerHTML = '<tr><td colspan="8" class="table__empty">正在加载…</td></tr>';
        return App.get('/api/announcements?' + params.toString()).then(function (data) {
            state.total = Number(data.total);
            state.current = Number(data.current);
            state.size = Number(data.size);
            state.loadedCount = data.records ? data.records.length : 0;
            renderRows(data.records);
            renderPager();
        }).catch(function (error) {
            el.body.innerHTML = '<tr><td colspan="8" class="table__empty">加载失败：'
                + App.escapeHtml(error.message) + '</td></tr>';
            App.toast(error.message, 'err');
        });
    }

    /* ------------------------------------------------------------ 弹窗 */

    function setFooterMode(mode) {
        el.btnSaveDraft.hidden = mode !== 'create';
        el.btnSavePublish.hidden = mode !== 'create';
        el.btnSaveEdit.hidden = mode !== 'edit';
    }

    function openModal(record) {
        state.editingId = record ? record.id : null;
        state.submitStatus = null;
        el.modalTitle.textContent = record ? '编辑公告' : '新建公告';
        el.error.textContent = '';
        setFooterMode(record ? 'edit' : 'create');

        setField('title', record ? record.title : '');
        setEditorContent(record ? record.content : '');
        setField('attachment', record ? record.attachment : '');

        el.modal.hidden = false;
        el.title.focus();
    }

    function closeModal() {
        el.modal.hidden = true;
        state.editingId = null;
        state.submitStatus = null;
        el.error.textContent = '';
    }

    /* ------------------------------------------------------------ 详情（只读） */

    function detailMetaHtml(record) {
        var items = [
            ['状态', statusBadge(record.status)],
            ['作者', App.dash(record.author)],
            ['发布时间', App.dash(App.formatDateTime(record.publishTime))],
            ['创建时间', App.dash(App.formatDateTime(record.createTime))],
            ['公告 ID', App.dash(record.id)]
        ];
        return items.map(function (item) {
            return '<div class="ann-detail__meta-item">'
                + '<span class="ann-detail__meta-key">' + item[0] + '</span>'
                + '<span class="ann-detail__meta-value">' + item[1] + '</span>'
                + '</div>';
        }).join('');
    }

    function detailAttachmentHtml(record) {
        if (!record.attachment) {
            return '<span class="text-muted">该公告没有附件</span>';
        }
        return '<a class="ann-detail__link" href="' + App.escapeHtml(record.attachment) + '"'
            + ' target="_blank" rel="noopener">' + App.escapeHtml(record.attachment) + '</a>';
    }

    /**
     * 渲染正文。
     *
     * <p>老公告的正文是纯文本（可能带换行），新公告是富文本 HTML，两者共存：
     * 含块级标签 → 清洗后 innerHTML（`.ann-detail__content` 用常规空白处理）；
     * 不含标签 → 原样 textContent 并加 `--plain`（靠 `white-space: pre-wrap` 保住换行）。
     */
    function renderContent(content) {
        var raw = content === null || content === undefined ? '' : String(content);
        if (!raw.trim()) {
            el.detailContent.textContent = '（无正文）';
            el.detailContent.classList.remove('ann-detail__content--plain');
            return;
        }
        if (RICH_BLOCK_TAGS.test(raw)) {
            el.detailContent.innerHTML = sanitizeRich(raw);
            el.detailContent.classList.remove('ann-detail__content--plain');
        } else {
            el.detailContent.textContent = raw;
            el.detailContent.classList.add('ann-detail__content--plain');
        }
    }

    /** 打开只读详情：数据按 id 现取，保证看到的是最新内容。 */
    function openDetail(id) {
        App.get('/api/announcements/' + id).then(function (record) {
            state.detailRecord = record;
            el.detailTitle.textContent = record.title ? String(record.title) : '公告详情';
            el.detailMeta.innerHTML = detailMetaHtml(record);
            renderContent(record.content);
            el.detailAttachment.innerHTML = detailAttachmentHtml(record);
            el.detailModal.hidden = false;
        }).catch(function (error) {
            App.toast(error.message || '加载公告详情失败', 'err');
        });
    }

    function closeDetail() {
        el.detailModal.hidden = true;
    }

    /**
     * 详情 → 编辑：关闭详情、打开编辑弹窗并回填正文。
     *
     * <p>复用详情已经取到的那条数据（刚展示给用户的正是它），不再多打一次请求。
     */
    function editFromDetail() {
        var record = state.detailRecord;
        if (!record) {
            return;
        }
        closeDetail();
        openModal(record);
    }

    function submitForm(event) {
        event.preventDefault();

        var content = getEditorContent();
        var title = fieldValue('title');
        if (!title) {
            el.error.textContent = '请填写公告标题';
            return;
        }
        if (!content) {
            el.error.textContent = '请填写公告内容';
            return;
        }
        // 字数按纯文字算（不含排版标记），与服务端校验口径一致
        var plainLength = quill ? quill.getText().trim().length : 0;
        if (plainLength > MAX_CONTENT_TEXT_LENGTH) {
            el.error.textContent = '正文最长 ' + MAX_CONTENT_TEXT_LENGTH + ' 字，当前 ' + plainLength + ' 字';
            return;
        }

        var payload = {
            title: title,
            content: content,
            attachment: fieldValue('attachment') || null
        };

        var editingId = state.editingId;
        // 新建时按按钮决定保存为草稿还是直接发布；编辑时不带 status，由发布/下线按钮单独控制
        if (editingId === null) {
            payload.status = state.submitStatus === null ? STATUS_DRAFT : state.submitStatus;
        }

        el.error.textContent = '';
        toggleButtons(true);

        var pending = editingId
            ? App.put('/api/announcements/' + editingId, payload)
            : App.post('/api/announcements', payload);

        pending.then(function () {
            closeModal();
            App.toast(editingId ? '公告修改成功' : '公告创建成功', 'ok');
            loadList();
        }).catch(function (error) {
            el.error.textContent = error.message || '保存失败';
        }).then(function () {
            toggleButtons(false);
        });
    }

    function toggleButtons(disabled) {
        [el.btnSaveDraft, el.btnSavePublish, el.btnSaveEdit].forEach(function (button) {
            button.disabled = disabled;
        });
    }

    function changeStatus(id, target, titleText) {
        var confirmText = target === 'publish'
            ? '确认发布公告「' + titleText + '」吗？发布后所有人在欢迎页可见。'
            : '确认下线公告「' + titleText + '」吗？下线后不再展示，记录保留。';
        if (!window.confirm(confirmText)) {
            return;
        }
        App.post('/api/announcements/' + id + '/' + target, {}).then(function () {
            App.toast(target === 'publish' ? '公告已发布' : '公告已下线', 'ok');
            loadList();
        }).catch(function (error) {
            App.toast(error.message || '操作失败', 'err');
        });
    }

    function removeRecord(id, titleText) {
        if (!window.confirm('确认删除公告「' + titleText + '」吗？删除后不可恢复。')) {
            return;
        }
        App.del('/api/announcements/' + id).then(function () {
            App.toast('公告删除成功', 'ok');
            if (state.loadedCount <= 1 && state.current > 1) {
                state.current = state.current - 1;
            }
            loadList();
        }).catch(function (error) {
            App.toast(error.message || '删除失败', 'err');
        });
    }

    /* ------------------------------------------------------------ 工具 */

    /** 点遮罩或任意 [data-close] 元素时关闭指定弹窗。 */
    function bindDismiss(modal, onClose) {
        modal.addEventListener('click', function (event) {
            if (event.target.closest && event.target.closest('[data-close]')) {
                onClose();
            }
        });
    }

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

    /** 从行内取标题文本，用于删除/发布确认提示。 */
    function titleOf(button) {
        var row = button.closest('tr');
        var cell = row ? row.querySelector('.ann-cell__title') : null;
        return cell ? cell.textContent : '该公告';
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

            var viewBtn = target.closest('[data-view]');
            if (viewBtn) {
                openDetail(viewBtn.getAttribute('data-view'));
                return;
            }

            var editBtn = target.closest('[data-edit]');
            if (editBtn) {
                var editId = editBtn.getAttribute('data-edit');
                App.get('/api/announcements/' + editId).then(function (record) {
                    openModal(record);
                }).catch(function (error) {
                    App.toast(error.message, 'err');
                });
                return;
            }

            var publishBtn = target.closest('[data-publish]');
            if (publishBtn) {
                changeStatus(publishBtn.getAttribute('data-publish'), 'publish', titleOf(publishBtn));
                return;
            }

            var offlineBtn = target.closest('[data-offline]');
            if (offlineBtn) {
                changeStatus(offlineBtn.getAttribute('data-offline'), 'offline', titleOf(offlineBtn));
                return;
            }

            var removeBtn = target.closest('[data-remove]');
            if (removeBtn) {
                removeRecord(removeBtn.getAttribute('data-remove'), titleOf(removeBtn));
            }
        });

        el.form.addEventListener('submit', submitForm);

        el.btnSaveDraft.addEventListener('click', function () {
            state.submitStatus = STATUS_DRAFT;
        });

        el.btnSavePublish.addEventListener('click', function () {
            state.submitStatus = STATUS_PUBLISHED;
        });

        // 两个弹窗共用同一套关闭规则：点遮罩、点 ×/关闭按钮、按 Esc
        bindDismiss(el.modal, closeModal);
        bindDismiss(el.detailModal, closeDetail);

        if (el.btnDetailEdit) {
            el.btnDetailEdit.addEventListener('click', editFromDetail);
        }

        document.addEventListener('keydown', function (event) {
            if (event.key !== 'Escape') {
                return;
            }
            if (!el.detailModal.hidden) {
                closeDetail();
                return;
            }
            if (!el.modal.hidden) {
                closeModal();
            }
        });
    }

    initEditor();
    bindEvents();
    loadList();
})(window, document);
