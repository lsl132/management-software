/* ==========================================================================
   公告管理页脚本：公告增删改查 + 发布/下线，全部 ajax，无整页刷新
   约定：作者由服务端按登录用户写入；发布时间只在首次发布时确定
   ========================================================================== */
(function (window, document) {
    'use strict';

    var App = window.App;

    var STATUS_DRAFT = 1;
    var STATUS_PUBLISHED = 2;
    var STATUS_OFFLINE = 3;

    var state = {
        current: 1,
        size: 10,
        keyword: '',
        status: '',
        total: 0,
        loadedCount: 0,
        editingId: null,
        /** 本次提交要附带的状态：草稿/发布由按钮决定，编辑时为 null（不变更状态） */
        submitStatus: null
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
        content: document.getElementById('content'),
        attachment: document.getElementById('attachment'),
        btnSaveDraft: document.getElementById('btnSaveDraft'),
        btnSavePublish: document.getElementById('btnSavePublish'),
        btnSaveEdit: document.getElementById('btnSaveEdit'),

        detailModal: document.getElementById('announcementDetailModal'),
        detailTitle: document.getElementById('announcementDetailTitle'),
        detailMeta: document.getElementById('announcementDetailMeta'),
        detailContent: document.getElementById('announcementDetailContent'),
        detailAttachment: document.getElementById('announcementDetailAttachment')
    };

    /* ------------------------------------------------------------ 渲染 */

    function statusBadge(status) {
        var text = status === STATUS_DRAFT ? '草稿'
            : status === STATUS_PUBLISHED ? '已发布'
                : status === STATUS_OFFLINE ? '已下线' : '未知';
        var cls = status === STATUS_PUBLISHED ? 'badge--ok'
            : status === STATUS_DRAFT ? 'badge--warn' : 'badge--muted';
        return '<span class="badge ' + cls + '">' + text + '</span>';
    }

    /** 标题列：标题本身可点击打开只读详情，下方是灰色摘要（不把长正文塞进表格）。 */
    function summaryCell(record) {
        var text = record.content ? String(record.content) : '';
        if (text.length > 40) {
            text = text.slice(0, 40) + '…';
        }
        return '<button type="button" class="ann-cell__title" data-view="' + record.id + '"'
            + ' title="查看公告详情">' + App.dash(record.title) + '</button>'
            + (text ? '<span class="ann-cell__summary">' + App.escapeHtml(text) + '</span>' : '');
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
                + '<td>' + App.dash(App.formatDateTime(row.publishTime)) + '</td>'
                + '<td>' + attachmentCell(row) + '</td>'
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
        setField('content', record ? record.content : '');
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

    /** 打开只读详情：数据按 id 现取，保证看到的是最新内容。 */
    function openDetail(id) {
        App.get('/api/announcements/' + id).then(function (record) {
            el.detailTitle.textContent = record.title ? String(record.title) : '公告详情';
            el.detailMeta.innerHTML = detailMetaHtml(record);
            el.detailContent.textContent = record.content ? String(record.content) : '（无正文）';
            el.detailAttachment.innerHTML = detailAttachmentHtml(record);
            el.detailModal.hidden = false;
        }).catch(function (error) {
            App.toast(error.message || '加载公告详情失败', 'err');
        });
    }

    function closeDetail() {
        el.detailModal.hidden = true;
    }

    function submitForm(event) {
        event.preventDefault();

        var payload = {
            title: fieldValue('title'),
            content: fieldValue('content'),
            attachment: fieldValue('attachment') || null
        };
        if (!payload.title) {
            el.error.textContent = '请填写公告标题';
            return;
        }
        if (!payload.content) {
            el.error.textContent = '请填写公告内容';
            return;
        }

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

    bindEvents();
    loadList();
})(window, document);
