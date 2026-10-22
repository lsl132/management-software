/* ==========================================================================
   公司公告页脚本（员工只读）：已发布公告的分页列表 + 只读详情
   只读入口：没有增删改，也没有状态切换；草稿与已下线由服务端过滤掉
   ========================================================================== */
(function (window, document) {
    'use strict';

    var App = window.App;

    var state = {
        current: 1,
        size: 10,
        keyword: '',
        total: 0
    };

    var el = {
        body: document.getElementById('noticeBody'),
        keyword: document.getElementById('keyword'),
        btnSearch: document.getElementById('btnSearch'),
        btnReset: document.getElementById('btnReset'),
        btnPrev: document.getElementById('btnPrev'),
        btnNext: document.getElementById('btnNext'),
        pageSize: document.getElementById('pageSize'),
        pagerInfo: document.getElementById('pagerInfo'),
        pageNow: document.getElementById('pageNow'),
        detailModal: document.getElementById('noticeDetailModal'),
        detailTitle: document.getElementById('noticeDetailTitle'),
        detailMeta: document.getElementById('noticeDetailMeta'),
        detailContent: document.getElementById('noticeDetailContent'),
        detailAttachment: document.getElementById('noticeDetailAttachment')
    };

    /* ------------------------------------------------------------ 列表 */

    function renderRows(records) {
        if (!records || records.length === 0) {
            el.body.innerHTML = '<tr><td colspan="4" class="table__empty">暂无已发布的公告</td></tr>';
            return;
        }
        el.body.innerHTML = records.map(function (row) {
            // 标题用 button：可点击但不是链接（详情是弹窗，没有独立 URL）
            var title = '<button type="button" class="notice-title" data-view="' + row.id + '">'
                + App.escapeHtml(row.title) + '</button>';
            var attachment = row.attachment
                ? '<a class="notice-link" href="' + App.escapeHtml(row.attachment)
                    + '" target="_blank" rel="noopener">查看附件</a>'
                : '<span class="text-muted">—</span>';
            return '<tr>'
                + '<td>' + title + '</td>'
                + '<td>' + App.dash(row.author) + '</td>'
                + '<td>' + App.dash(App.formatDateTime(row.publishTime)) + '</td>'
                + '<td>' + attachment + '</td>'
                + '</tr>';
        }).join('');
    }

    function renderPager() {
        var pages = Math.max(1, Math.ceil(state.total / state.size));
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

        el.body.innerHTML = '<tr><td colspan="4" class="table__empty">正在加载…</td></tr>';
        App.get('/api/notices?' + params.toString()).then(function (data) {
            // 分页计数显式转数字：接口按 JSON 数字下发，这里仍兜一道，
            // 否则 current + 1 一旦变成字符串拼接就会翻到畸形页码
            state.total = Number(data.total);
            state.current = Number(data.current);
            state.size = Number(data.size);
            renderRows(data.records);
            renderPager();
        }).catch(function (error) {
            el.body.innerHTML = '<tr><td colspan="4" class="table__empty">加载失败：'
                + App.escapeHtml(error.message) + '</td></tr>';
            App.toast(error.message, 'err');
        });
    }

    /* ------------------------------------------------------------ 详情 */

    function metaHtml(record) {
        var items = [
            ['发布人', App.dash(record.author)],
            ['发布时间', App.dash(App.formatDateTime(record.publishTime))]
        ];
        return items.map(function (item) {
            return '<div class="ann-detail__meta-item">'
                + '<span class="ann-detail__meta-key">' + item[0] + '</span>'
                + '<span class="ann-detail__meta-value">' + item[1] + '</span>'
                + '</div>';
        }).join('');
    }

    function attachmentHtml(record) {
        if (!record.attachment) {
            return '<span class="text-muted">该公告没有附件</span>';
        }
        return '<a class="ann-detail__link" href="' + App.escapeHtml(record.attachment) + '"'
            + ' target="_blank" rel="noopener">' + App.escapeHtml(record.attachment) + '</a>';
    }

    function openDetail(id) {
        App.get('/api/notices/' + id).then(function (record) {
            el.detailTitle.textContent = record.title ? String(record.title) : '公告详情';
            el.detailMeta.innerHTML = metaHtml(record);
            App.renderRichContent(el.detailContent, record.content);
            el.detailAttachment.innerHTML = attachmentHtml(record);
            el.detailModal.hidden = false;
        }).catch(function (error) {
            App.toast(error.message || '公告加载失败', 'err');
        });
    }

    function closeDetail() {
        el.detailModal.hidden = true;
    }

    /* ------------------------------------------------------------ 事件 */

    function bindEvents() {
        el.btnSearch.addEventListener('click', function () {
            state.keyword = el.keyword.value.trim();
            state.current = 1;
            loadList();
        });

        el.btnReset.addEventListener('click', function () {
            el.keyword.value = '';
            state.keyword = '';
            state.current = 1;
            loadList();
        });

        el.keyword.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                el.btnSearch.click();
            }
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
            var pages = Math.max(1, Math.ceil(state.total / state.size));
            if (state.current < pages) {
                state.current = state.current + 1;
                loadList();
            }
        });

        // 事件代理：表格行是动态渲染的，逐个绑定会在每次翻页后失效
        el.body.addEventListener('click', function (event) {
            var target = event.target;
            var viewBtn = target.closest ? target.closest('[data-view]') : null;
            if (viewBtn) {
                openDetail(viewBtn.getAttribute('data-view'));
            }
        });

        el.detailModal.addEventListener('click', function (event) {
            var target = event.target;
            if (target.closest && target.closest('[data-close]')) {
                closeDetail();
            }
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && !el.detailModal.hidden) {
                closeDetail();
            }
        });
    }

    bindEvents();
    loadList();
})(window, document);
