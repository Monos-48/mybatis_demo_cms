/* Atlas CMS 管理端（原生 JS，无外部依赖） */
(() => {
    'use strict';

    /**
     * 计算应用根路径（包含 Tomcat context path）。
     * 示例：http://host/cms/js/app.js -> /cms/
     * 部署到 /、/cms、/mybatis_demo_cms_war_exploded 时均可用。
     */
    const APP_BASE = (() => {
        const script = document.currentScript;
        if (script && script.src) {
            return new URL('../', new URL(script.src, window.location.href)).pathname;
        }
        return window.location.pathname.replace(/[^/]*$/, '') || '/';
    })();

    function appUrl(path) {
        return APP_BASE + String(path).replace(/^\/+/, '');
    }

    const STATUS_TEXT = {0: '草稿', 1: '待审核', 2: '审核通过', 3: '已驳回', 4: '已发布', 5: '已下架'};
    const STATUS_CLASS = {0: 'draft', 1: 'pending', 2: 'approved', 3: 'rejected', 4: 'published', 5: 'offline'};
    const REVISION_TEXT = {0: '修订草稿', 1: '修订待审', 2: '修订通过', 3: '修订驳回'};
    const USER_STATUS = {0: '禁用', 1: '正常', 2: '待审批'};
    const PERM_GROUP = {article: '文章内容', category: '内容分类', user: '用户管理', role: '角色权限', log: '操作日志'};
    const PAGE_META = {
        dashboard: ['CONTENT OPERATIONS', '工作台'],
        articles: ['CONTENT LIFECYCLE', '文章管理'],
        categories: ['CONTENT STRUCTURE', '分类管理'],
        users: ['ACCESS CONTROL', '用户管理'],
        roles: ['RBAC / PERMISSIONS', '角色权限'],
        logs: ['AUDIT TRAIL', '操作日志']
    };
    const NAV = [
        {id: 'dashboard', label: '工作台', icon: '◈', perm: null},
        {id: 'articles', label: '文章管理', icon: '▤', perm: 'article:list'},
        {id: 'categories', label: '分类管理', icon: '⌘', perm: 'category:list'},
        {id: 'users', label: '用户管理', icon: '♟', perm: 'user:manage'},
        {id: 'roles', label: '角色权限', icon: '⛨', perm: 'role:manage'},
        {id: 'logs', label: '操作日志', icon: '◷', perm: 'log:view'}
    ];

    const state = {
        me: null,
        page: 'dashboard',
        allTree: [],
        roles: [],
        permissions: [],
        rolesLoaded: false,
        article: {pageNum: 1, pageSize: 10, title: '', categoryId: '', status: '', startDate: '', endDate: '', sort: 'createTime|desc'},
        articleRows: [],
        selectedArticles: new Set(),
        user: {pageNum: 1, pageSize: 10, keyword: '', status: ''},
        log: {pageNum: 1, pageSize: 10, module: '', username: '', success: '', startDate: '', endDate: ''},
        logRows: []
    };
    let navSeq = 0;
    let serviceTimer = null;

    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

    function esc(value) {
        return String(value == null ? '' : value).replace(/[&<>"']/g, (ch) => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[ch]));
    }

    function qs(params) {
        const query = new URLSearchParams();
        Object.entries(params).forEach(([key, value]) => {
            if (value !== '' && value !== null && value !== undefined) {
                query.append(key, value);
            }
        });
        return query.toString();
    }

    function toast(message, type = '') {
        const root = $('#toast-root');
        const node = document.createElement('div');
        node.className = `toast ${type}`.trim();
        const icon = type === 'success' ? '✓' : type === 'error' ? '!' : 'i';
        node.innerHTML = `<span class="toast-icon">${icon}</span><span>${esc(message)}</span>`;
        root.appendChild(node);
        setTimeout(() => node.remove(), type === 'error' ? 4200 : 3200);
    }

    /** 按钮忙碌态：防止重复提交，并在请求结束后恢复。 */
    function setBusy(button, busy, busyText) {
        if (!button) return;
        if (busy) {
            if (!button.dataset.originalHtml) button.dataset.originalHtml = button.innerHTML;
            button.classList.add('is-busy');
            button.disabled = true;
            if (busyText) button.textContent = busyText;
        } else {
            button.classList.remove('is-busy');
            button.disabled = false;
            if (button.dataset.originalHtml) {
                button.innerHTML = button.dataset.originalHtml;
                delete button.dataset.originalHtml;
            }
        }
    }

    function skeleton(rows = 4) {
        return `<div class="card skeleton-card">${Array.from({length: rows}, (_, index) =>
            `<div class="skeleton-line ${index === 0 ? 'w-30' : index % 2 ? 'w-90' : 'w-60'}"></div>`).join('')}</div>`;
    }

    /** 与应用风格一致的二次确认，返回 Promise<boolean>，不会覆盖当前编辑弹窗。 */
    function confirmDialog({title = '请确认操作', message, confirmText = '确定', cancelText = '取消', danger = false}) {
        return new Promise((resolve) => {
            const root = $('#confirm-root');
            root.innerHTML = `
                <div class="modal-backdrop confirm-backdrop">
                    <div class="modal confirm-modal" role="alertdialog" aria-modal="true" aria-label="${esc(title)}">
                        <div class="confirm-body">
                            <div class="confirm-icon ${danger ? 'danger' : ''}">${danger ? '!' : '?'}</div>
                            <h3 style="margin:0 0 8px;font-size:17px">${esc(title)}</h3>
                            <div class="muted" style="font-size:13px;line-height:1.7">${esc(message)}</div>
                        </div>
                        <div class="confirm-actions">
                            <button type="button" class="btn" data-confirm-cancel>${esc(cancelText)}</button>
                            <button type="button" class="btn ${danger ? 'btn-danger-solid' : 'btn-primary'}" data-confirm-ok>${esc(confirmText)}</button>
                        </div>
                    </div>
                </div>`;
            const finish = (value) => {
                root.innerHTML = '';
                document.removeEventListener('keydown', onKey);
                resolve(value);
            };
            const onKey = (event) => {
                if (event.key === 'Escape') finish(false);
                if (event.key === 'Enter') finish(true);
            };
            $('[data-confirm-cancel]', root).addEventListener('click', () => finish(false));
            $('[data-confirm-ok]', root).addEventListener('click', () => finish(true));
            $('.confirm-backdrop', root).addEventListener('mousedown', (event) => {
                if (event.target === event.currentTarget) finish(false);
            });
            document.addEventListener('keydown', onKey);
            setTimeout(() => $('[data-confirm-ok]', root)?.focus(), 30);
        });
    }

    function handleUnauthorized() {
        if (!state.me) return;
        state.me = null;
        closeModal(true);
        showLogin();
        toast('登录状态已失效，请重新登录', 'error');
    }

    async function api(path, options = {}) {
        const {method = 'GET', body, auth = true, timeout = 15000} = options;
        const headers = {Accept: 'application/json'};
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), timeout);
        let response;
        try {
            response = await fetch(appUrl(path), {
                method,
                headers,
                credentials: 'same-origin',
                signal: controller.signal,
                body: body === undefined ? undefined : JSON.stringify(body)
            });
        } catch (error) {
            if (error.name === 'AbortError') {
                throw new Error('请求超时，请检查服务状态后重试');
            }
            throw new Error('网络请求失败，请确认服务已启动或网络连接正常');
        } finally {
            clearTimeout(timer);
        }
        let payload = null;
        try {
            payload = await response.json();
        } catch (error) {
            payload = null;
        }
        if (response.status === 401 && auth) {
            handleUnauthorized();
            throw new Error((payload && payload.message) || '登录已过期');
        }
        if (!response.ok || !payload || payload.code !== 200) {
            throw new Error((payload && payload.message) || `请求失败（HTTP ${response.status}）`);
        }
        return payload.data;
    }

    function has(permission) {
        return !!state.me && state.me.permissions.includes(permission);
    }

    function isManager() {
        return !!state.me && (state.me.roles.includes('ADMIN') || state.me.roles.includes('EDITOR'));
    }

    function fmt(value) {
        return value || '—';
    }

    function articleBadge(status) {
        return `<span class="badge badge-${STATUS_CLASS[status] || 'draft'}">${STATUS_TEXT[status] || '未知'}</span>`;
    }

    function userBadge(status) {
        const cls = status === 1 ? 'published' : status === 0 ? 'offline' : 'pending';
        return `<span class="badge badge-${cls}">${USER_STATUS[status] || '未知'}</span>`;
    }

    function hideBoot() {
        const boot = $('#boot-screen');
        if (!boot || boot.classList.contains('hidden')) return;
        boot.classList.add('leaving');
        setTimeout(() => boot.classList.add('hidden'), 220);
    }

    function resetUserState() {
        state.me = null;
        state.page = 'dashboard';
        state.allTree = [];
        state.roles = [];
        state.permissions = [];
        state.rolesLoaded = false;
        state.articleRows = [];
        state.selectedArticles.clear();
        state.logRows = [];
        closeModal(true);
    }

    function showLogin(clearPassword = true) {
        if (clearPassword) $('#login-password').value = '';
        resetUserState();
        showRegisterPane(false);
        $('#login-error').hidden = true;
        $('#app').classList.add('hidden');
        $('#login-screen').classList.remove('hidden');
        setSidebar(false);
        hideBoot();
        setTimeout(() => $('#login-username').focus(), 50);
    }

    function showApp() {
        $('#login-screen').classList.add('hidden');
        $('#app').classList.remove('hidden');
        const name = state.me.nickname || state.me.username;
        $('#user-name').textContent = name;
        $('#user-account').textContent = '@' + state.me.username;
        $('#avatar').textContent = name.slice(0, 1).toUpperCase();
        $('#role-chip').textContent = state.me.roles.join(' · ');
        hideBoot();
    }

    function setSidebar(open) {
        $('#app').classList.toggle('nav-open', open);
        $('#sidebar-backdrop').hidden = !open;
        $('#sidebar-toggle').setAttribute('aria-expanded', String(open));
    }

    function pageFromHash() {
        let id = window.location.hash.replace(/^#\/?/, '');
        try {
            id = decodeURIComponent(id);
        } catch (error) { /* 非法编码时回退到工作台 */ }
        return NAV.some((item) => item.id === id) ? id : 'dashboard';
    }

    function renderNav() {
        const nav = $('#nav');
        nav.innerHTML = NAV.filter((item) => !item.perm || has(item.perm)).map((item) =>
            `<button type="button" data-page="${item.id}" class="${state.page === item.id ? 'active' : ''}" ${state.page === item.id ? 'aria-current="page"' : ''}>
                <span class="ico" aria-hidden="true">${item.icon}</span><span>${item.label}</span>
            </button>`).join('');
        $$('#nav button').forEach((button) => {
            button.addEventListener('click', () => {
                setSidebar(false);
                navigate(button.dataset.page);
            });
        });
    }

    function navigate(page, options = {}) {
        const target = NAV.find((item) => item.id === page);
        if (!target || (target.perm && !has(target.perm))) {
            toast('没有访问该模块的权限', 'error');
            if (options.fromHash) {
                history.replaceState(null, '', '#/' + state.page);
            }
            return;
        }
        const seq = ++navSeq;
        state.page = page;
        renderNav();
        if (!options.fromHash) {
            const hash = '#/' + page;
            if (window.location.hash !== hash) window.location.hash = hash;
        }
        const meta = PAGE_META[page];
        $('#page-kicker').textContent = meta[0];
        $('#page-title').textContent = meta[1];
        $('#content').innerHTML = skeleton();
        window.scrollTo({top: 0, behavior: 'smooth'});
        const renderers = {
            dashboard: renderDashboard,
            articles: renderArticles,
            categories: renderCategories,
            users: renderUsers,
            roles: renderRoles,
            logs: renderLogs
        };
        renderers[page]().catch((error) => {
            if (seq !== navSeq) return;
            $('#content').innerHTML = `<div class="card"><div class="empty-state"><span class="empty-icon">⚠</span><strong>加载失败</strong>${esc(error.message)}</div></div>`;
        });
    }

    window.addEventListener('hashchange', () => {
        const page = pageFromHash();
        if (page !== state.page && state.me) navigate(page, {fromHash: true});
    });

    async function boot() {
        try {
            state.me = await api('/api/auth/me', {auth: false, timeout: 5000});
            await enterApp();
        } catch (error) {
            showLogin(false);
        } finally {
            hideBoot();
            probeService();
            serviceTimer = setInterval(probeService, 60000);
        }
    }

    async function enterApp() {
        showApp();
        if (has('category:list')) {
            try {
                state.allTree = await api('/api/categories/tree');
            } catch (error) {
                state.allTree = [];
            }
        }
        let page = pageFromHash();
        const target = NAV.find((item) => item.id === page);
        if (!target || (target.perm && !has(target.perm))) page = 'dashboard';
        if (window.location.hash !== '#/' + page) {
            history.replaceState(null, '', '#/' + page);
        }
        navigate(page, {fromHash: true});
    }

    /* ------------------------------ 服务状态 ------------------------------ */
    async function probeService() {
        const dot = $('#service-dot');
        const label = $('#service-status');
        if (!label) return;
        dot.classList.remove('is-offline');
        dot.classList.add('is-pending');
        label.textContent = '正在检测服务…';
        try {
            await api('/api/health', {auth: false, timeout: 5000});
            dot.classList.remove('is-pending', 'is-offline');
            label.textContent = '服务运行中';
        } catch (error) {
            dot.classList.remove('is-pending');
            dot.classList.add('is-offline');
            label.textContent = '服务不可用';
        }
    }

    /* ------------------------------ 弹窗 ------------------------------ */
    let modalGuard = null;

    function openModal({title, subtitle = '', body, footer = '', size = '', onMount}) {
        modalGuard = null;
        closeModal(true);
        const root = $('#modal-root');
        root.innerHTML = `
            <div class="modal-backdrop">
                <div class="modal ${size}">
                    <div class="modal-head">
                        <div><h3>${esc(title)}</h3>${subtitle ? `<p>${esc(subtitle)}</p>` : ''}</div>
                        <button type="button" class="modal-close" aria-label="关闭">×</button>
                    </div>
                    <div class="modal-body">${body}</div>
                    ${footer ? `<div class="modal-foot">${footer}</div>` : ''}
                </div>
            </div>`;
        document.body.style.overflow = 'hidden';
        $('.modal-close', root).addEventListener('click', () => closeModal());
        $('.modal-backdrop', root).addEventListener('mousedown', (event) => {
            if (event.target === event.currentTarget) closeModal();
        });
        if (onMount) onMount(root);
    }

    function closeModal(force = false) {
        if (!force && modalGuard && modalGuard()) {
            confirmDialog({
                title: '放弃未保存的修改？',
                message: '当前表单有尚未保存的内容，关闭后这些修改将丢失。',
                confirmText: '放弃修改',
                cancelText: '继续编辑',
                danger: true
            }).then((ok) => {
                if (ok) closeModal(true);
            });
            return;
        }
        modalGuard = null;
        $('#modal-root').innerHTML = '';
        document.body.style.overflow = '';
    }

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && $('#confirm-root').innerHTML === '') closeModal();
    });

    /* ------------------------------ 分类工具 ------------------------------ */
    function flattenTree(nodes, depth = 0, output = []) {
        (nodes || []).forEach((node) => {
            output.push({...node, _depth: depth});
            flattenTree(node.children || [], depth + 1, output);
        });
        return output;
    }

    function categoryOptions(nodes, selected, depth = 0, exclude = new Set()) {
        let html = '';
        (nodes || []).forEach((node) => {
            if (!exclude.has(node.id)) {
                html += `<option value="${node.id}" ${String(node.id) === String(selected) ? 'selected' : ''}>${'　'.repeat(depth)}${esc(node.name)}</option>`;
            }
            html += categoryOptions(node.children || [], selected, depth + 1, exclude);
        });
        return html;
    }

    function findCategory(nodes, id) {
        for (const node of nodes || []) {
            if (node.id === id) return node;
            const found = findCategory(node.children || [], id);
            if (found) return found;
        }
        return null;
    }

    function descendantIds(node, output = new Set()) {
        (node.children || []).forEach((child) => {
            output.add(child.id);
            descendantIds(child, output);
        });
        return output;
    }

    function pagerHtml(page, label = '条', options = {}) {
        const sizes = options.sizes;
        const sizePicker = sizes ? `<label class="page-size">每页
                <select data-page-size>${sizes.map((size) => `<option value="${size}" ${Number(options.pageSize) === size ? 'selected' : ''}>${size}</option>`).join('')}</select>
            </label>` : '';
        return `<div class="pagination-info"><span>第 ${page.pageNum} / ${Math.max(page.pages, 1)} 页 · 共 ${page.total} ${label}</span>${sizePicker}</div>
            <div class="pagination-actions">
                <button type="button" class="btn btn-sm" data-page="${page.pageNum - 1}" ${page.pageNum <= 1 ? 'disabled' : ''}>上一页</button>
                <button type="button" class="btn btn-sm" data-page="${page.pageNum + 1}" ${page.pageNum >= page.pages ? 'disabled' : ''}>下一页</button>
            </div>`;
    }

    function bindPager(container, page, onPage, onSize) {
        $$('button[data-page]', container).forEach((button) => {
            button.addEventListener('click', () => {
                const next = Number(button.dataset.page);
                if (next >= 1 && next <= page.pages) onPage(next);
            });
        });
        const select = $('[data-page-size]', container);
        if (select && onSize) {
            select.addEventListener('change', () => onSize(Number(select.value)));
        }
    }

    /* ------------------------------ 工作台 ------------------------------ */
    async function renderDashboard() {
        const canList = has('article:list');
        let allPage = {total: '—', records: []};
        let pendingPage = {total: '—'};
        let publishedPage = {total: '—'};
        if (canList) {
            const [all, pending, published] = await Promise.all([
                api('/api/articles?pageNum=1&pageSize=5&sortField=createTime&sortOrder=desc'),
                api('/api/articles?pageNum=1&pageSize=1&status=1'),
                api('/api/articles?pageNum=1&pageSize=1&status=4')
            ]);
            allPage = all;
            pendingPage = pending;
            publishedPage = published;
        }
        const categories = has('category:list') ? flattenTree(state.allTree) : [];
        const recent = allPage.records || [];
        const name = esc(state.me.nickname || state.me.username);
        const roleNames = state.me.roles.map((role) => role === 'ADMIN' ? '超级管理员' : role === 'EDITOR' ? '内容编辑' : role === 'AUTHOR' ? '内容作者' : role === 'VIEWER' ? '只读访客' : role).join(' / ');
        const clickable = (attrs) => canList ? ` class="stat-card clickable" tabindex="0" role="button" ${attrs}` : ' class="stat-card"';

        $('#content').innerHTML = `
            <section class="hero-card">
                <h3>你好，${name}</h3>
                <p>今天也请让内容保持有序：从分类规划、草稿撰写，到审核发布与操作审计，所有动作都在同一套权限边界内完成。</p>
                <div class="hero-meta"><span>当前身份：${esc(roleNames)}</span><span>权限点：${state.me.permissions.length} 项</span><span>系统状态：<span id="hero-service">运行中</span></span></div>
            </section>
            <section class="stat-grid">
                <div${clickable('data-article-status=""')}><div class="stat-label">全部文章</div><div class="stat-value">${allPage.total}</div><div class="stat-foot">覆盖草稿、审核与发布全状态</div>${canList ? '<div class="stat-more">查看全部 →</div>' : ''}</div>
                <div${clickable('data-article-status="1"')}><div class="stat-label">待审核</div><div class="stat-value">${pendingPage.total}</div><div class="stat-foot">${has('article:audit') ? '等待你给出审核结论' : '由内容编辑处理'}</div>${canList ? '<div class="stat-more">进入审核队列 →</div>' : ''}</div>
                <div${clickable('data-article-status="4"')}><div class="stat-label">已发布</div><div class="stat-value">${publishedPage.total}</div><div class="stat-foot">线上可见内容</div>${canList ? '<div class="stat-more">查看已发布 →</div>' : ''}</div>
                ${has('category:list') ? `<div class="stat-card clickable" tabindex="0" role="button" data-go="categories"><div class="stat-label">分类节点</div><div class="stat-value">${categories.length}</div><div class="stat-foot">多级分类树节点总数</div><div class="stat-more">管理分类 →</div></div>` : '<div class="stat-card"><div class="stat-label">分类节点</div><div class="stat-value">—</div><div class="stat-foot">当前角色无分类查看权限</div></div>'}
            </section>
            <section class="dashboard-grid">
                <div class="card">
                    <div class="section-title"><h4>最近内容</h4>${canList ? '<button class="btn-link" data-go="articles">查看全部 →</button>' : ''}</div>
                    <div class="table-wrap">
                        <table class="data-table">
                            <thead><tr><th>标题</th><th>分类</th><th>状态</th><th>更新时间</th></tr></thead>
                            <tbody>
                            ${recent.length ? recent.map((article) => `
                                <tr class="recent-row" data-article-id="${article.id}" tabindex="0">
                                    <td class="title-cell"><span class="title-main">${esc(article.title)}</span><span class="title-sub">${esc(article.authorName || '—')}</span></td>
                                    <td>${esc(article.categoryName || '—')}</td>
                                    <td>${articleBadge(article.status)}</td>
                                    <td class="cell-time">${fmt(article.updateTime)}</td>
                                </tr>`).join('') : `<tr><td colspan="4"><div class="empty-state"><span class="empty-icon">▤</span><strong>${canList ? '暂无内容' : '暂无文章查看权限'}</strong>${canList ? '新建第一篇内容后，这里会展示最近动态' : '请联系管理员分配 article:list 权限'}</div></td></tr>`}
                            </tbody>
                        </table>
                    </div>
                </div>
                <div class="card">
                    <div class="section-title"><h4>内容流转指南</h4><small>状态机由后端统一校验</small></div>
                    <div class="workflow">
                        <div class="workflow-step"><span class="workflow-num">01</span><div><b>草稿 / 驳回后修改</b><span>作者维护内容，保存后仍可继续编辑。</span></div></div>
                        <div class="workflow-step"><span class="workflow-num">02</span><div><b>提交审核</b><span>进入待审核队列，作者不能再直接修改。</span></div></div>
                        <div class="workflow-step"><span class="workflow-num">03</span><div><b>编辑审核</b><span>通过或驳回；驳回必须填写审核意见。</span></div></div>
                        <div class="workflow-step"><span class="workflow-num">04</span><div><b>发布 / 下架</b><span>审核通过后可发布，已发布内容可随时下架。</span></div></div>
                    </div>
                </div>
            </section>`;

        const goArticles = (status) => {
            state.article = {...state.article, pageNum: 1, status};
            navigate('articles');
        };
        $$('[data-article-status]', $('#content')).forEach((card) => {
            const open = () => goArticles(card.dataset.articleStatus);
            card.addEventListener('click', open);
            card.addEventListener('keydown', (event) => {
                if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); open(); }
            });
        });
        $$('[data-go]', $('#content')).forEach((button) => {
            button.addEventListener('click', () => navigate(button.dataset.go));
        });
        $$('.recent-row', $('#content')).forEach((row) => {
            const open = () => openArticleDetail(Number(row.dataset.articleId));
            row.addEventListener('click', open);
            row.addEventListener('keydown', (event) => {
                if (event.key === 'Enter') { event.preventDefault(); open(); }
            });
        });
    }

    /* ------------------------------ 文章 ------------------------------ */
    async function renderArticles() {
        if (!state.allTree.length && has('category:list')) {
            try { state.allTree = await api('/api/categories/tree'); } catch (error) { /* ignore */ }
        }
        $('#content').innerHTML = `
            <div class="toolbar">
                <div class="toolbar-actions">
                    ${has('article:create') ? '<button class="btn btn-primary" id="new-article">＋ 新建文章</button>' : ''}
                    ${has('article:publish') ? '<button class="btn" id="batch-publish">批量发布</button>' : ''}
                    ${has('article:offline') ? '<button class="btn" id="batch-offline">批量下架</button>' : ''}
                </div>
                <button class="btn btn-ghost" id="refresh-articles">↻ 刷新</button>
            </div>
            <form class="card filters" id="article-filters">
                <label class="field"><span>标题关键字</span><input name="title" value="${esc(state.article.title)}" placeholder="模糊搜索标题" maxlength="100"></label>
                <label class="field"><span>分类</span><select name="categoryId"><option value="">全部分类</option>${categoryOptions(state.allTree, state.article.categoryId)}</select></label>
                <label class="field"><span>状态</span><select name="status"><option value="">全部状态</option>${Object.entries(STATUS_TEXT).map(([key, value]) => `<option value="${key}" ${String(state.article.status) === key ? 'selected' : ''}>${value}</option>`).join('')}</select></label>
                <label class="field"><span>开始日期</span><input type="date" name="startDate" value="${esc(state.article.startDate)}"></label>
                <label class="field"><span>结束日期</span><input type="date" name="endDate" value="${esc(state.article.endDate)}"></label>
                <label class="field"><span>排序</span><select name="sort">
                    <option value="createTime|desc" ${state.article.sort === 'createTime|desc' ? 'selected' : ''}>最新创建</option>
                    <option value="createTime|asc" ${state.article.sort === 'createTime|asc' ? 'selected' : ''}>最早创建</option>
                    <option value="publishTime|desc" ${state.article.sort === 'publishTime|desc' ? 'selected' : ''}>最近发布</option>
                    <option value="viewCount|desc" ${state.article.sort === 'viewCount|desc' ? 'selected' : ''}>阅读最多</option>
                    <option value="title|asc" ${state.article.sort === 'title|asc' ? 'selected' : ''}>标题升序</option>
                </select></label>
                <div class="filter-actions"><button class="btn btn-primary" type="submit">筛选</button><button class="btn" type="button" id="reset-article-filter">重置</button></div>
            </form>
            <div class="selection-bar" id="selection-bar" hidden>
                <span>已选择 <b id="sel-count">0</b> 篇文章</span>
                <span class="muted">可执行批量发布 / 下架</span>
                <button type="button" class="btn btn-sm" id="clear-selection">取消选择</button>
            </div>
            <div class="card">
                <div class="table-wrap">
                    <table class="data-table">
                        <thead><tr>
                            <th style="width:38px"><input type="checkbox" id="article-check-all" title="全选本页" aria-label="全选本页"></th>
                            <th>文章</th><th>分类 / 作者</th><th>状态</th><th>发布时间</th><th>更新时间</th><th style="width:230px">操作</th>
                        </tr></thead>
                        <tbody id="article-body"><tr><td colspan="7"><div class="empty-state">正在加载…</div></td></tr></tbody>
                    </table>
                </div>
                <div class="pagination" id="article-pager"></div>
            </div>`;

        $('#article-filters').addEventListener('submit', (event) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            const startDate = form.get('startDate') || '';
            const endDate = form.get('endDate') || '';
            if (startDate && endDate && startDate > endDate) {
                toast('开始日期不能晚于结束日期', 'error');
                return;
            }
            state.article.title = String(form.get('title') || '').trim();
            state.article.categoryId = form.get('categoryId') || '';
            state.article.status = form.get('status') || '';
            state.article.startDate = startDate;
            state.article.endDate = endDate;
            state.article.sort = form.get('sort') || 'createTime|desc';
            state.article.pageNum = 1;
            loadArticles();
        });
        $('#reset-article-filter').addEventListener('click', () => {
            state.article = {...state.article, pageNum: 1, title: '', categoryId: '', status: '', startDate: '', endDate: '', sort: 'createTime|desc'};
            state.selectedArticles.clear();
            renderArticles();
        });
        $('#refresh-articles').addEventListener('click', () => loadArticles());
        $('#new-article')?.addEventListener('click', () => openArticleForm());
        $('#batch-publish')?.addEventListener('click', () => batchChange('publish'));
        $('#batch-offline')?.addEventListener('click', () => batchChange('offline'));
        $('#clear-selection').addEventListener('click', () => {
            state.selectedArticles.clear();
            $$('.row-check', $('#article-body')).forEach((checkbox) => { checkbox.checked = false; });
            updateArticleSelection();
        });
        $('#article-check-all').addEventListener('change', (event) => {
            $$('.row-check', $('#article-body')).forEach((checkbox) => {
                checkbox.checked = event.target.checked;
                if (checkbox.checked) state.selectedArticles.add(Number(checkbox.value));
                else state.selectedArticles.delete(Number(checkbox.value));
            });
            updateArticleSelection();
        });
        $('#article-body').addEventListener('click', (event) => {
            const button = event.target.closest('button[data-action]');
            if (!button) return;
            handleArticleAction(button.dataset.action, Number(button.dataset.id));
        });
        $('#article-body').addEventListener('change', (event) => {
            if (!event.target.classList.contains('row-check')) return;
            const id = Number(event.target.value);
            if (event.target.checked) state.selectedArticles.add(id);
            else state.selectedArticles.delete(id);
            updateArticleSelection();
        });
        await loadArticles();
    }

    function updateArticleSelection() {
        const bar = $('#selection-bar');
        if (!bar) return;
        bar.hidden = state.selectedArticles.size === 0;
        $('#sel-count').textContent = state.selectedArticles.size;
        const pageIds = state.articleRows.map((article) => article.id);
        const all = $('#article-check-all');
        if (!all) return;
        const checkedOnPage = pageIds.filter((id) => state.selectedArticles.has(id)).length;
        all.checked = pageIds.length > 0 && checkedOnPage === pageIds.length;
        all.indeterminate = checkedOnPage > 0 && checkedOnPage < pageIds.length;
    }

    function articleQuery() {
        const [sortField, sortOrder] = state.article.sort.split('|');
        return qs({
            pageNum: state.article.pageNum,
            pageSize: state.article.pageSize,
            title: state.article.title,
            categoryId: state.article.categoryId,
            status: state.article.status,
            startDate: state.article.startDate,
            endDate: state.article.endDate,
            sortField,
            sortOrder
        });
    }

    async function loadArticles() {
        state.selectedArticles.clear();
        updateArticleSelection();
        try {
            const page = await api('/api/articles?' + articleQuery());
            state.articleRows = page.records || [];
            const body = $('#article-body');
            if (!state.articleRows.length) {
                body.innerHTML = `<tr><td colspan="7"><div class="empty-state"><span class="empty-icon">▤</span><strong>没有匹配的文章</strong>调整筛选条件${has('article:create') ? '，或新建一篇内容' : ''}</div></td></tr>`;
            } else {
                body.innerHTML = state.articleRows.map((article) => {
                    const owner = article.authorId === state.me.id;
                    const editableStatus = article.status === 0 || article.status === 3;
                    const publishedLike = article.status === 4 || article.status === 5;
                    const canWrite = has('article:edit') && (isManager() || owner) && editableStatus;
                    const canSubmit = has('article:submit') && (isManager() || owner) && editableStatus;
                    const canRevision = has('article:edit') && (isManager() || owner)
                        && publishedLike && article.revisionStatus !== 1;
                    const canDelete = has('article:delete') && (isManager() || owner) && (isManager() || editableStatus);
                    const actions = [`<button class="btn-link" data-action="detail" data-id="${article.id}">详情</button>`];
                    if (canWrite) actions.push(`<button class="btn-link" data-action="edit" data-id="${article.id}">编辑</button>`);
                    if (canSubmit) actions.push(`<button class="btn-link" data-action="submit" data-id="${article.id}">提交审核</button>`);
                    if (canRevision) actions.push(`<button class="btn-link" data-action="edit-revision" data-id="${article.id}">${article.revisionStatus === 3 ? '继续修订' : '修订'}</button>`);
                    if (article.status === 1 && has('article:audit')) actions.push(`<button class="btn-link" data-action="audit" data-id="${article.id}">审核</button>`);
                    if (article.revisionStatus === 1 && has('article:audit')) actions.push(`<button class="btn-link" data-action="audit-revision" data-id="${article.id}">审核修订</button>`);
                    if ((article.status === 2 || article.status === 5) && has('article:publish') && article.revisionStatus !== 1) actions.push(`<button class="btn-link" data-action="publish" data-id="${article.id}">发布</button>`);
                    if (article.status === 4 && has('article:offline')) actions.push(`<button class="btn-link" data-action="offline" data-id="${article.id}">下架</button>`);
                    if (canDelete) actions.push(`<button class="btn-link danger" data-action="delete" data-id="${article.id}">删除</button>`);
                    const revisionTag = article.revisionStatus != null
                        ? `<span class="tag" style="margin-left:6px">${REVISION_TEXT[article.revisionStatus] || '修订'}</span>`
                        : '';
                    return `<tr>
                        <td><input class="row-check" type="checkbox" value="${article.id}"></td>
                        <td class="title-cell"><span class="title-main" title="${esc(article.title)}">${esc(article.title)}</span><span class="title-sub">v${article.version} · 阅读 ${article.viewCount || 0}</span></td>
                        <td><div>${esc(article.categoryName || '—')}</div><div class="small muted">${esc(article.authorName || '—')}</div></td>
                        <td>${articleBadge(article.status)}${revisionTag}${article.auditRemark ? `<div class="small muted" style="max-width:160px;white-space:normal">${esc(article.auditRemark)}</div>` : ''}</td>
                        <td class="cell-time">${fmt(article.publishTime)}</td>
                        <td class="cell-time">${fmt(article.updateTime)}</td>
                        <td><div class="action-group">${actions.join('')}</div></td>
                    </tr>`;
                }).join('');
            }
            updateArticleSelection();
            $('#article-pager').innerHTML = pagerHtml(page, '篇文章', {sizes: [10, 20, 50], pageSize: state.article.pageSize});
            bindPager($('#article-pager'), page, (next) => {
                state.article.pageNum = next;
                loadArticles();
            }, (size) => {
                state.article.pageSize = size;
                state.article.pageNum = 1;
                loadArticles();
            });
        } catch (error) {
            $('#article-body').innerHTML = `<tr><td colspan="7"><div class="empty-state"><span class="empty-icon">⚠</span>${esc(error.message)}</div></td></tr>`;
            toast(error.message, 'error');
        }
    }

    async function handleArticleAction(action, id) {
        try {
            if (action === 'detail') return openArticleDetail(id);
            if (action === 'edit') return openArticleForm(id);
            if (action === 'edit-revision') return openArticleForm(id, {revision: true});
            if (action === 'audit') return openAuditModal(id);
            if (action === 'audit-revision') return openAuditModal(id, {revision: true});
            if (action === 'delete') {
                if (!(await confirmDialog({title: '删除文章', message: '删除后不可在列表中恢复，确定继续吗？', confirmText: '删除', danger: true}))) return;
                await api(`/api/articles/${id}`, {method: 'DELETE'});
                toast('文章已删除', 'success');
                await loadArticles();
                return;
            }
            if (action === 'submit') {
                if (!(await confirmDialog({title: '提交审核', message: '提交后文章将进入待审核队列且不可编辑，确定继续吗？', confirmText: '提交审核'}))) return;
                await api(`/api/articles/${id}/submit`, {method: 'POST'});
                toast('已提交审核', 'success');
                await loadArticles();
                return;
            }
            if (action === 'publish' || action === 'offline') {
                const label = action === 'publish' ? '发布' : '下架';
                if (!(await confirmDialog({title: label + '文章', message: `确定${label}这篇文章吗？`, confirmText: label}))) return;
                await api(`/api/articles/${id}/${action}`, {method: 'POST'});
                toast(`文章已${label}`, 'success');
                await loadArticles();
            }
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    async function batchChange(action) {
        const ids = Array.from(state.selectedArticles);
        if (!ids.length) {
            toast('请先勾选要操作的文章', 'error');
            return;
        }
        const label = action === 'publish' ? '发布' : '下架';
        if (!(await confirmDialog({title: `批量${label}`, message: `确定批量${label}选中的 ${ids.length} 篇文章吗？`, confirmText: `批量${label}`}))) return;
        try {
            await api(`/api/articles/batch/${action}`, {method: 'POST', body: {articleIds: ids}});
            toast(`批量${label}成功`, 'success');
            await loadArticles();
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    async function openArticleDetail(id) {
        try {
            const article = await api(`/api/articles/${id}`);
            const owner = article.authorId === state.me.id;
            const editable = (article.status === 0 || article.status === 3)
                && has('article:edit') && (isManager() || owner);
            let revision = null;
            if (article.status === 4 || article.status === 5) {
                try {
                    revision = await api(`/api/articles/${id}/revision`);
                } catch (error) {
                    revision = null;
                }
            }
            const revisionEditable = (article.status === 4 || article.status === 5)
                && has('article:edit') && (isManager() || owner)
                && (!revision || revision.status !== 1);
            const revisionAuditable = revision && revision.status === 1 && has('article:audit');
            const revisionMeta = revision ? `
                <div><dt>修订状态</dt><dd>${REVISION_TEXT[revision.status] || '修订'}</dd></div>
                <div><dt>修订更新时间</dt><dd>${fmt(revision.updateTime)}</dd></div>
                ${revision.auditRemark ? `<div><dt>修订审核意见</dt><dd>${esc(revision.auditRemark)}</dd></div>` : ''}` : '';
            openModal({
                title: article.title,
                subtitle: `${article.categoryName || '未分类'} · ${article.authorName || '未知作者'}`,
                size: 'modal-wide',
                body: `
                    <dl class="detail-meta">
                        <div><dt>状态</dt><dd>${articleBadge(article.status)}</dd></div>
                        <div><dt>发布时间</dt><dd>${fmt(article.publishTime)}</dd></div>
                        <div><dt>更新时间</dt><dd>${fmt(article.updateTime)}</dd></div>
                        <div><dt>版本 / 阅读</dt><dd>v${article.version} · ${article.viewCount || 0}</dd></div>
                        <div><dt>审核人</dt><dd>${esc(article.auditorName || '—')}</dd></div>
                        <div><dt>审核意见</dt><dd>${esc(article.auditRemark || '—')}</dd></div>
                        ${revisionMeta}
                    </dl>
                    ${article.summary ? `<p class="muted" style="line-height:1.7">${esc(article.summary)}</p>` : ''}
                    <div class="detail-content">${esc(article.content)}</div>
                    ${revision ? `<h4 style="margin:18px 0 8px;font-size:13px">${revision.status === 1 ? '待审核修订内容' : '最近修订内容'}</h4>
                        <div class="detail-content" style="max-height:200px;overflow:auto;background:#f8fafc;padding:14px;border-radius:10px">${esc(revision.content)}</div>` : ''}`,
                footer: `<button class="btn" data-close>关闭</button>
                    ${editable ? '<button class="btn" data-edit-article>编辑文章</button>' : ''}
                    ${revisionEditable ? `<button class="btn ${editable ? '' : 'btn-primary'}" data-edit-revision>${revision && revision.status === 3 ? '继续修订' : '编辑修订'}</button>` : ''}
                    ${revisionAuditable ? '<button class="btn btn-primary" data-audit-revision>审核修订</button>' : ''}`
            });
            $('[data-close]').addEventListener('click', () => closeModal());
            $('[data-edit-article]')?.addEventListener('click', () => {
                closeModal(true);
                openArticleForm(article.id);
            });
            $('[data-edit-revision]')?.addEventListener('click', () => {
                closeModal(true);
                openArticleForm(article.id, {revision: true});
            });
            $('[data-audit-revision]')?.addEventListener('click', () => {
                closeModal(true);
                openAuditModal(article.id, {revision: true});
            });
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    async function openArticleForm(id, formOptions = {}) {
        try {
            const revisionMode = Boolean(formOptions.revision);
            const tree = await api('/api/categories/tree?enabledOnly=true');
            const article = id ? await api(`/api/articles/${id}`) : null;
            if (revisionMode) {
                if (!article || (article.status !== 4 && article.status !== 5)) {
                    toast('只有已发布或已下架文章可以创建修订', 'error');
                    return;
                }
            } else if (article && article.status !== 0 && article.status !== 3) {
                toast('只有草稿或已驳回文章可编辑', 'error');
                return;
            }

            let revision = null;
            if (revisionMode) {
                revision = await api(`/api/articles/${id}/revision`);
                if (revision && revision.status === 1) {
                    toast('修订正在审核中，不能修改', 'error');
                    return;
                }
            }
            const source = revision || article;
            const options = categoryOptions(tree, source ? source.categoryId : '');
            if (!options) {
                toast('暂无启用的分类，请先创建分类', 'error');
                return;
            }
            const initial = {
                title: source ? source.title : '',
                categoryId: source ? String(source.categoryId) : '',
                summary: source ? (source.summary || '') : '',
                content: source ? source.content : ''
            };
            const titleText = revisionMode
                ? (revision ? '编辑修订' : '创建修订')
                : (article ? '编辑文章' : '新建文章');
            const subtitle = revisionMode
                ? `线上版本：${STATUS_TEXT[article.status]}${revision ? ` · 修订状态：${REVISION_TEXT[revision.status]}` : ' · 保存后不影响线上内容'}`
                : (article ? `当前状态：${STATUS_TEXT[article.status]} · 保存使用乐观锁校验版本` : '保存为草稿，或直接提交审核');
            openModal({
                title: titleText,
                subtitle,
                size: 'modal-lg',
                body: `
                    <form id="article-form" class="form-grid" autocomplete="off">
                        ${revisionMode ? '<p class="muted small span-2">修订审核通过前，读者仍看到当前线上版本。</p>' : ''}
                        <label class="field span-2"><span>标题 *<small class="char-count" id="af-title-count">0 / 100</small></span>
                            <input id="af-title" maxlength="100" value="${esc(initial.title)}" placeholder="2-100 个字符"></label>
                        <label class="field"><span>分类 *</span><select id="af-category"><option value="">请选择分类</option>${options}</select></label>
                        <label class="field"><span>摘要<small class="char-count" id="af-summary-count">0 / 500</small></span>
                            <input id="af-summary" maxlength="500" value="${esc(initial.summary)}" placeholder="选填，最多 500 字"></label>
                        <label class="field span-2"><span>正文 *<small class="char-count" id="af-content-count">0 字</small></span>
                            <textarea id="af-content" style="min-height:240px" placeholder="至少 10 个字符">${esc(initial.content)}</textarea></label>
                    </form>`,
                footer: `
                    <button class="btn" data-close>取消</button>
                    <button class="btn" id="save-draft">${revisionMode ? '保存修订草稿' : '保存草稿'}</button>
                    <button class="btn btn-primary" id="save-submit">${revisionMode ? '保存并提交修订审核' : '保存并提交审核'}</button>`
            });
            const updateCounts = () => {
                $('#af-title-count').textContent = `${$('#af-title').value.length} / 100`;
                $('#af-summary-count').textContent = `${$('#af-summary').value.length} / 500`;
                $('#af-content-count').textContent = `${$('#af-content').value.length} 字`;
            };
            ['#af-title', '#af-summary', '#af-content', '#af-category'].forEach((selector) => {
                $(selector).addEventListener('input', updateCounts);
            });
            updateCounts();
            modalGuard = () => $('#af-title').value !== initial.title
                || $('#af-category').value !== initial.categoryId
                || $('#af-summary').value !== initial.summary
                || $('#af-content').value !== initial.content;
            $('[data-close]').addEventListener('click', () => closeModal());
            const save = async (submitForReview, button) => {
                const title = $('#af-title').value.trim();
                const categoryId = $('#af-category').value;
                const summary = $('#af-summary').value.trim();
                const content = $('#af-content').value;
                if (title.length < 2) return toast('标题至少 2 个字符', 'error');
                if (!categoryId) return toast('请选择分类', 'error');
                if (content.trim().length < 10) return toast('正文至少 10 个字符', 'error');
                const payload = {
                    title,
                    categoryId: Number(categoryId),
                    summary,
                    content,
                    submitForReview
                };
                const buttons = [$('#save-draft'), $('#save-submit')];
                buttons.forEach((item) => setBusy(item, true));
                try {
                    if (revisionMode) {
                        payload.version = revision ? revision.version : 0;
                        await api(`/api/articles/${article.id}/revision`, {method: 'PUT', body: payload});
                        toast(submitForReview ? '修订已提交审核' : '修订草稿已保存', 'success');
                    } else if (article) {
                        payload.version = article.version;
                        await api(`/api/articles/${article.id}`, {method: 'PUT', body: payload});
                        toast(submitForReview ? '已保存并提交审核' : '已保存草稿', 'success');
                    } else {
                        await api('/api/articles', {method: 'POST', body: payload});
                        toast(submitForReview ? '已保存并提交审核' : '已保存草稿', 'success');
                    }
                    closeModal(true);
                    await loadArticles();
                } catch (error) {
                    toast(error.message, 'error');
                } finally {
                    buttons.forEach((item) => setBusy(item, false));
                }
            };
            $('#save-draft').addEventListener('click', (event) => save(false, event.currentTarget));
            $('#save-submit').addEventListener('click', (event) => save(true, event.currentTarget));
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    async function openAuditModal(id, formOptions = {}) {
        try {
            const revisionMode = Boolean(formOptions.revision);
            const article = await api(`/api/articles/${id}`);
            let revision = null;
            if (revisionMode) {
                revision = await api(`/api/articles/${id}/revision`);
                if (!revision || revision.status !== 1) {
                    toast('没有待审核修订', 'error');
                    return;
                }
            }
            const source = revisionMode ? revision : article;
            openModal({
                title: revisionMode ? '审核修订' : '审核文章',
                subtitle: source.title,
                body: `
                    <div class="detail-meta">
                        <div><dt>分类</dt><dd>${esc(article.categoryName || '—')}</dd></div>
                        <div><dt>作者</dt><dd>${esc(article.authorName || '—')}</dd></div>
                        <div><dt>提交时间</dt><dd>${fmt(source.updateTime)}</dd></div>
                        <div><dt>状态</dt><dd>${revisionMode ? '<span class="tag">修订待审核</span>' : articleBadge(article.status)}</dd></div>
                    </div>
                    <p class="muted" style="line-height:1.7">${esc(source.summary || '无摘要')}</p>
                    <div class="detail-content" style="max-height:240px;overflow:auto;padding:14px;background:#f8fafc;border-radius:10px">${esc(source.content)}</div>
                    <div class="field" style="margin-top:18px"><span>审核结论 *</span>
                        <div class="check-list">
                            <label class="check-item"><input type="radio" name="audit-result" value="approve" checked> ${revisionMode ? '通过并替换线上内容' : '审核通过'}</label>
                            <label class="check-item"><input type="radio" name="audit-result" value="reject"> 驳回修改</label>
                        </div>
                    </div>
                    <label class="field"><span>审核意见<small class="char-count">驳回必填</small></span><textarea id="audit-remark" maxlength="500" placeholder="通过可选填；驳回时必须填写审核意见"></textarea></label>`,
                footer: '<button class="btn" data-close>取消</button><button class="btn btn-primary" id="audit-submit">提交审核结论</button>'
            });
            $('[data-close]').addEventListener('click', () => closeModal());
            $$('input[name="audit-result"]').forEach((radio) => {
                radio.addEventListener('change', () => {
                    if (radio.value === 'reject' && radio.checked) $('#audit-remark').focus();
                });
            });
            $('#audit-submit').addEventListener('click', async (event) => {
                const approved = $('input[name="audit-result"]:checked').value === 'approve';
                const remark = $('#audit-remark').value.trim();
                if (!approved && !remark) return toast('驳回时必须填写审核意见', 'error');
                const button = event.currentTarget;
                setBusy(button, true);
                try {
                    const endpoint = revisionMode
                        ? `/api/articles/${id}/revision/audit`
                        : `/api/articles/${id}/audit`;
                    await api(endpoint, {method: 'POST', body: {approved, remark}});
                    closeModal(true);
                    if (revisionMode) {
                        toast(approved ? '修订已通过，线上内容已替换' : '修订已驳回', 'success');
                    } else {
                        toast(approved ? '审核通过' : '已驳回', 'success');
                    }
                    await loadArticles();
                } catch (error) {
                    toast(error.message, 'error');
                } finally {
                    setBusy(button, false);
                }
            });
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    /* ------------------------------ 分类 ------------------------------ */
    async function loadTree() {
        state.allTree = await api('/api/categories/tree');
        return state.allTree;
    }

    async function renderCategories() {
        await loadTree();
        const rows = flattenTree(state.allTree);
        const manage = has('category:manage');
        const enabled = rows.filter((node) => node.effectiveStatus === 1).length;
        $('#content').innerHTML = `
            <div class="toolbar">
                <div class="toolbar-actions">${manage ? '<button class="btn btn-primary" id="new-category">＋ 新建顶级分类</button>' : ''}</div>
                <div class="toolbar-actions">
                    <span class="muted small">共 ${rows.length} 个节点 · 有效启用 ${enabled} 个 · 父级停用时整棵子树不可用</span>
                    <button class="btn btn-ghost" id="refresh-categories">↻ 刷新</button>
                </div>
            </div>
            <div class="card">
                <div class="table-wrap"><table class="data-table">
                    <thead><tr><th>分类名称</th><th>排序</th><th>状态</th><th>描述</th><th>创建时间</th>${manage ? '<th style="width:260px">操作</th>' : ''}</tr></thead>
                    <tbody>
                    ${rows.length ? rows.map((node) => `
                        <tr class="category-tree-row">
                            <td><div class="tree-name" style="padding-left:${node._depth * 24}px">${node._depth ? '<span class="tree-branch">└</span>' : ''}<b>${esc(node.name)}</b>${node.children && node.children.length ? `<span class="tag">${node.children.length} 子级</span>` : ''}</div></td>
                            <td>${node.sort}</td>
                            <td>${node.status !== 1
                                ? '<span class="badge badge-offline">停用</span>'
                                : (node.effectiveStatus === 0
                                    ? '<span class="badge badge-offline" title="父级分类已停用">继承停用</span>'
                                    : '<span class="badge badge-published">启用</span>')}</td>
                            <td class="muted">${esc(node.description || '—')}</td>
                            <td class="cell-time">${fmt(node.createTime)}</td>
                            ${manage ? `<td><div class="action-group">
                                <button class="btn-link" data-cat-action="child" data-id="${node.id}">添加子级</button>
                                <button class="btn-link" data-cat-action="edit" data-id="${node.id}">编辑</button>
                                <button class="btn-link" data-cat-action="toggle" data-id="${node.id}">${node.status === 1 ? '停用' : '启用'}</button>
                                <button class="btn-link danger" data-cat-action="delete" data-id="${node.id}">删除</button>
                            </div></td>` : ''}
                        </tr>`).join('') : '<tr><td colspan="6"><div class="empty-state"><span class="empty-icon">⌘</span><strong>暂无分类</strong>创建顶级分类后即可挂载文章</div></td></tr>'}
                    </tbody>
                </table></div>
            </div>`;
        $('#new-category')?.addEventListener('click', () => openCategoryForm(null, 0));
        $('#refresh-categories').addEventListener('click', () => renderCategories());
        $('.data-table', $('#content')).addEventListener('click', (event) => {
            const button = event.target.closest('button[data-cat-action]');
            if (!button) return;
            const id = Number(button.dataset.id);
            const node = findCategory(state.allTree, id);
            if (!node) return;
            if (button.dataset.catAction === 'child') openCategoryForm(null, id);
            if (button.dataset.catAction === 'edit') openCategoryForm(node, node.parentId);
            if (button.dataset.catAction === 'toggle') toggleCategory(node);
            if (button.dataset.catAction === 'delete') deleteCategory(node);
        });
    }

    async function openCategoryForm(node, parentId) {
        const exclude = node ? descendantIds(node) : new Set();
        if (node) exclude.add(node.id);
        const options = categoryOptions(state.allTree, node ? node.parentId : parentId, 0, exclude);
        openModal({
            title: node ? '编辑分类' : '新建分类',
            subtitle: '分类支持多级树结构；父分类停用后，整棵子树不可挂载新文章且读者不可见',
            body: `<form class="form-grid">
                <label class="field span-2"><span>分类名称 *</span><input id="cf-name" maxlength="50" value="${esc(node ? node.name : '')}" placeholder="2-50 个字符"></label>
                <label class="field"><span>父分类</span><select id="cf-parent"><option value="0">（顶级分类）</option>${options}</select></label>
                <label class="field"><span>排序值</span><input id="cf-sort" type="number" min="0" max="9999" value="${node ? node.sort : 0}"></label>
                <label class="field"><span>状态</span><select id="cf-status"><option value="1" ${!node || node.status === 1 ? 'selected' : ''}>启用</option><option value="0" ${node && node.status === 0 ? 'selected' : ''}>停用</option></select></label>
                <label class="field span-2"><span>描述</span><input id="cf-desc" maxlength="200" value="${esc(node ? (node.description || '') : '')}"></label>
            </form>`,
            footer: '<button class="btn" data-close>取消</button><button class="btn btn-primary" id="category-save">保存</button>'
        });
        $('[data-close]').addEventListener('click', () => closeModal());
        $('#category-save').addEventListener('click', async (event) => {
            const name = $('#cf-name').value.trim();
            if (name.length < 2) return toast('分类名称至少 2 个字符', 'error');
            const payload = {
                name,
                parentId: Number($('#cf-parent').value),
                sort: Number($('#cf-sort').value || 0),
                status: Number($('#cf-status').value),
                description: $('#cf-desc').value.trim()
            };
            const button = event.currentTarget;
            setBusy(button, true);
            try {
                if (node) await api(`/api/categories/${node.id}`, {method: 'PUT', body: payload});
                else await api('/api/categories', {method: 'POST', body: payload});
                closeModal(true);
                toast('分类已保存', 'success');
                await renderCategories();
            } catch (error) {
                toast(error.message, 'error');
            } finally {
                setBusy(button, false);
            }
        });
    }

    async function toggleCategory(node) {
        const next = node.status === 1 ? 0 : 1;
        const hasChildren = Boolean(node.children && node.children.length);
        const message = next === 0 && hasChildren
            ? `停用后其 ${node.children.length} 个子级分类也将不可用，确定继续吗？`
            : `确定${next === 1 ? '启用' : '停用'}分类「${node.name}」吗？`;
        if (!(await confirmDialog({title: next === 1 ? '启用分类' : '停用分类', message, confirmText: next === 1 ? '启用' : '停用'}))) return;
        try {
            await api(`/api/categories/${node.id}/status`, {method: 'PUT', body: {status: next}});
            toast('分类状态已更新', 'success');
            await renderCategories();
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    async function deleteCategory(node) {
        if (!(await confirmDialog({title: '删除分类', message: `确定删除分类「${node.name}」吗？存在子分类或文章时将被拒绝。`, confirmText: '删除', danger: true}))) return;
        try {
            await api(`/api/categories/${node.id}`, {method: 'DELETE'});
            toast('分类已删除', 'success');
            await renderCategories();
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    /* ------------------------------ 用户 ------------------------------ */
    async function ensureRoleData() {
        if (state.rolesLoaded) return;
        const [roles, permissions] = await Promise.all([
            api('/api/roles'),
            api('/api/permissions')
        ]);
        state.roles = roles || [];
        state.permissions = permissions || [];
        state.rolesLoaded = true;
    }

    async function renderUsers() {
        try {
            await ensureRoleData();
        } catch (error) {
            // 拥有用户管理权限但无角色管理权限时，仍允许查看/启停用户
            state.roles = [];
            state.permissions = [];
            state.rolesLoaded = true;
        }
        $('#content').innerHTML = `
            <div class="toolbar">
                <div class="toolbar-actions"><button class="btn btn-primary" id="new-user">＋ 新增用户</button></div>
                <div class="toolbar-actions">
                    <span class="muted small">注册用户默认为“待审批”，启用后方可登录</span>
                    <button class="btn btn-ghost" id="refresh-users">↻ 刷新</button>
                </div>
            </div>
            <form class="card filters" id="user-filters">
                <label class="field"><span>关键字</span><input name="keyword" value="${esc(state.user.keyword)}" placeholder="用户名或昵称"></label>
                <label class="field"><span>状态</span><select name="status"><option value="">全部状态</option><option value="1" ${String(state.user.status) === '1' ? 'selected' : ''}>正常</option><option value="2" ${String(state.user.status) === '2' ? 'selected' : ''}>待审批</option><option value="0" ${String(state.user.status) === '0' ? 'selected' : ''}>禁用</option></select></label>
                <div class="filter-actions"><button class="btn btn-primary" type="submit">筛选</button><button class="btn" type="button" id="reset-user-filter">重置</button></div>
            </form>
            <div class="card">
                <div class="table-wrap"><table class="data-table">
                    <thead><tr><th>用户</th><th>角色</th><th>状态</th><th>最后登录</th><th>创建时间</th><th style="width:280px">操作</th></tr></thead>
                    <tbody id="user-body"><tr><td colspan="6"><div class="empty-state">正在加载…</div></td></tr></tbody>
                </table></div>
                <div class="pagination" id="user-pager"></div>
            </div>`;
        $('#user-filters').addEventListener('submit', (event) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            state.user.keyword = String(form.get('keyword') || '').trim();
            state.user.status = form.get('status') || '';
            state.user.pageNum = 1;
            loadUsers();
        });
        $('#reset-user-filter').addEventListener('click', () => {
            state.user = {...state.user, pageNum: 1, keyword: '', status: ''};
            renderUsers();
        });
        $('#new-user').addEventListener('click', openUserForm);
        $('#refresh-users').addEventListener('click', () => loadUsers());
        $('#user-body').addEventListener('click', (event) => {
            const button = event.target.closest('button[data-user-action]');
            if (!button) return;
            handleUserAction(button.dataset.userAction, Number(button.dataset.id));
        });
        await loadUsers();
    }

    async function loadUsers() {
        try {
            const page = await api('/api/users?' + qs(state.user));
            const body = $('#user-body');
            if (!page.records.length) {
                body.innerHTML = '<tr><td colspan="6"><div class="empty-state"><span class="empty-icon">♟</span><strong>没有匹配的用户</strong>调整关键字或状态筛选后重试</div></td></tr>';
            } else {
                body.innerHTML = page.records.map((user) => {
                    const self = user.id === state.me.id;
                    const roleTags = (user.roles || []).map((role) => `<span class="tag ${role.roleCode === 'ADMIN' ? 'tag-blue' : role.roleCode === 'EDITOR' ? 'tag-green' : ''}">${esc(role.roleName)}</span>`).join('') || '<span class="muted small">未分配</span>';
                    let statusAction;
                    if (user.status === 2) statusAction = `<button class="btn-link" data-user-action="approve" data-id="${user.id}">审批通过</button>`;
                    else if (user.status === 1) statusAction = `<button class="btn-link danger" data-user-action="disable" data-id="${user.id}" ${self ? 'disabled' : ''}>禁用</button>`;
                    else statusAction = `<button class="btn-link" data-user-action="enable" data-id="${user.id}">启用</button>`;
                    return `<tr>
                        <td><div><b>${esc(user.nickname)}</b> ${self ? '<span class="tag tag-blue">当前账号</span>' : ''}</div><div class="small muted">@${esc(user.username)} · ${esc(user.email || '无邮箱')}</div></td>
                        <td>${roleTags}</td>
                        <td>${userBadge(user.status)}</td>
                        <td class="cell-time">${fmt(user.lastLoginTime)}</td>
                        <td class="cell-time">${fmt(user.createTime)}</td>
                        <td><div class="action-group">
                            ${statusAction}
                            <button class="btn-link" data-user-action="roles" data-id="${user.id}">分配角色</button>
                            <button class="btn-link" data-user-action="password" data-id="${user.id}">重置密码</button>
                            ${self ? '' : `<button class="btn-link danger" data-user-action="delete" data-id="${user.id}">删除</button>`}
                        </div></td>
                    </tr>`;
                }).join('');
            }
            $('#user-pager').innerHTML = pagerHtml(page, '个用户');
            bindPager($('#user-pager'), page, (next) => {
                state.user.pageNum = next;
                loadUsers();
            });
        } catch (error) {
            $('#user-body').innerHTML = `<tr><td colspan="6"><div class="empty-state">${esc(error.message)}</div></td></tr>`;
            toast(error.message, 'error');
        }
    }

    function roleCheckboxes(selectedIds = []) {
        if (!state.roles.length) return '<span class="muted small">暂无可分配角色</span>';
        return `<div class="check-list">${state.roles.map((role) => `
            <label class="check-item"><input type="checkbox" name="user-role" value="${role.id}" ${selectedIds.includes(role.id) ? 'checked' : ''}>
                <span>${esc(role.roleName)}<small>${esc(role.roleCode)}</small></span></label>`).join('')}</div>`;
    }

    function openUserForm() {
        openModal({
            title: '新增用户',
            subtitle: '由管理员直接创建，可立即启用',
            body: `<form class="form-grid">
                <label class="field"><span>用户名 *</span><input id="uf-username" maxlength="32" placeholder="4-32 位字母数字下划线"></label>
                <label class="field"><span>初始密码 *</span><input id="uf-password" type="password" maxlength="32" placeholder="8-32 位，含字母和数字"></label>
                <label class="field"><span>昵称 *</span><input id="uf-nickname" maxlength="20"></label>
                <label class="field"><span>邮箱</span><input id="uf-email" type="email" maxlength="100"></label>
                <label class="field"><span>状态</span><select id="uf-status"><option value="1">正常</option><option value="0">禁用</option></select></label>
                <div class="field span-2"><span>角色</span>${roleCheckboxes([])}</div>
            </form>`,
            footer: '<button class="btn" data-close>取消</button><button class="btn btn-primary" id="user-save">创建用户</button>'
        });
        $('[data-close]').addEventListener('click', () => closeModal());
        $('#user-save').addEventListener('click', async (event) => {
            const username = $('#uf-username').value.trim();
            const password = $('#uf-password').value;
            const nickname = $('#uf-nickname').value.trim();
            if (!/^[A-Za-z0-9_]{4,32}$/.test(username)) return toast('用户名需为 4-32 位字母、数字或下划线', 'error');
            if (password.length < 8) return toast('密码至少 8 位', 'error');
            if (!nickname) return toast('请填写昵称', 'error');
            const roleIds = $$('input[name="user-role"]:checked').map((input) => Number(input.value));
            const payload = {
                username,
                password,
                nickname,
                email: $('#uf-email').value.trim(),
                status: Number($('#uf-status').value),
                roleIds
            };
            const button = event.currentTarget;
            setBusy(button, true);
            try {
                await api('/api/users', {method: 'POST', body: payload});
                closeModal(true);
                toast('用户创建成功', 'success');
                await loadUsers();
            } catch (error) {
                toast(error.message, 'error');
            } finally {
                setBusy(button, false);
            }
        });
    }

    async function handleUserAction(action, id) {
        const row = await api(`/api/users/${id}`).catch(() => null);
        if (!row) return;
        try {
            if (action === 'approve' || action === 'enable' || action === 'disable') {
                const status = action === 'disable' ? 0 : 1;
                const label = status === 1 ? '启用' : '禁用';
                if (!(await confirmDialog({title: label + '用户', message: `确定${label}用户「${row.nickname}」吗？`, confirmText: label, danger: status === 0}))) return;
                await api(`/api/users/${id}/status`, {method: 'PUT', body: {status}});
                toast(`用户已${label}`, 'success');
                await loadUsers();
                return;
            }
            if (action === 'roles') return openUserRoles(row);
            if (action === 'password') return openResetPassword(row);
            if (action === 'delete') {
                if (!(await confirmDialog({title: '删除用户', message: `确定删除用户「${row.nickname}」吗？`, confirmText: '删除', danger: true}))) return;
                await api(`/api/users/${id}`, {method: 'DELETE'});
                toast('用户已删除', 'success');
                await loadUsers();
            }
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    function openUserRoles(user) {
        openModal({
            title: '分配角色',
            subtitle: `${user.nickname}（@${user.username}）`,
            body: `<div class="field"><span>角色（可多选）</span>${roleCheckboxes(user.roleIds || [])}</div>
                   <p class="muted small">角色决定用户可访问的接口权限；修改后用户下次进入页面时重新加载权限。</p>`,
            footer: '<button class="btn" data-close>取消</button><button class="btn btn-primary" id="roles-save">保存</button>'
        });
        $('[data-close]').addEventListener('click', () => closeModal());
        $('#roles-save').addEventListener('click', async (event) => {
            const roleIds = $$('input[name="user-role"]:checked').map((input) => Number(input.value));
            const button = event.currentTarget;
            setBusy(button, true);
            try {
                await api(`/api/users/${user.id}/roles`, {method: 'PUT', body: {roleIds}});
                closeModal(true);
                toast('角色已更新', 'success');
                await loadUsers();
            } catch (error) {
                toast(error.message, 'error');
            } finally {
                setBusy(button, false);
            }
        });
    }

    function openResetPassword(user) {
        openModal({
            title: '重置密码',
            subtitle: `${user.nickname}（@${user.username}）`,
            body: `<label class="field"><span>新密码 *</span><input id="rp-password" type="password" maxlength="32" placeholder="8-32 位，含字母和数字" autocomplete="new-password"></label>
                   <p class="muted small">重置后请通过安全渠道将新密码告知用户。</p>`,
            footer: '<button class="btn" data-close>取消</button><button class="btn btn-primary" id="rp-save">重置</button>'
        });
        $('[data-close]').addEventListener('click', () => closeModal());
        $('#rp-save').addEventListener('click', async (event) => {
            const password = $('#rp-password').value;
            if (password.length < 8) return toast('密码至少 8 位', 'error');
            const button = event.currentTarget;
            setBusy(button, true);
            try {
                await api(`/api/users/${user.id}/password`, {method: 'PUT', body: {newPassword: password}});
                closeModal(true);
                toast('密码已重置', 'success');
            } catch (error) {
                toast(error.message, 'error');
            } finally {
                setBusy(button, false);
            }
        });
    }

    /* ------------------------------ 角色权限 ------------------------------ */
    async function renderRoles() {
        state.rolesLoaded = false;
        await ensureRoleData();
        const roles = state.roles;
        $('#content').innerHTML = `
            <div class="toolbar">
                <div class="toolbar-actions"><button class="btn btn-primary" id="new-role">＋ 新建角色</button></div>
                <div class="toolbar-actions">
                    <span class="muted small">共 ${roles.length} 个角色 · 权限码通过 Spring AOP 在接口层校验</span>
                    <button class="btn btn-ghost" id="refresh-roles">↻ 刷新</button>
                </div>
            </div>
            <div class="role-grid">
                ${roles.length ? roles.map((role) => `
                    <div class="role-card">
                        <div class="role-card-head">
                            <div><h4>${esc(role.roleName)}</h4><span class="role-code">${esc(role.roleCode)}</span></div>
                            ${role.status === 1 ? '<span class="badge badge-published">启用</span>' : '<span class="badge badge-offline">停用</span>'}
                        </div>
                        <p class="role-desc">${esc(role.remark || '暂无备注')}</p>
                        <div class="role-foot">
                            <span class="muted small">${(role.permissionIds || []).length} / ${state.permissions.length} 个权限点</span>
                            <div class="action-group">
                                <button class="btn-link" data-role-action="edit" data-id="${role.id}">编辑</button>
                                <button class="btn-link" data-role-action="perms" data-id="${role.id}">配置权限</button>
                                ${role.roleCode === 'ADMIN' ? '' : `<button class="btn-link danger" data-role-action="delete" data-id="${role.id}">删除</button>`}
                            </div>
                        </div>
                    </div>`).join('') : '<div class="card empty-state"><span class="empty-icon">⛨</span><strong>暂无角色</strong>新建角色并分配权限后，用户才能访问对应模块</div>'}
            </div>`;
        $('#new-role').addEventListener('click', () => openRoleForm(null));
        $('#refresh-roles').addEventListener('click', () => renderRoles());
        $('.role-grid', $('#content')).addEventListener('click', (event) => {
            const button = event.target.closest('button[data-role-action]');
            if (!button) return;
            const role = state.roles.find((item) => item.id === Number(button.dataset.id));
            if (!role) return;
            if (button.dataset.roleAction === 'edit') openRoleForm(role);
            if (button.dataset.roleAction === 'perms') openRolePermissions(role);
            if (button.dataset.roleAction === 'delete') deleteRole(role);
        });
    }

    function openRoleForm(role) {
        openModal({
            title: role ? '编辑角色' : '新建角色',
            body: `<form class="form-grid">
                <label class="field"><span>角色名称 *</span><input id="rf-name" maxlength="30" value="${esc(role ? role.roleName : '')}"></label>
                <label class="field"><span>角色编码 *</span><input id="rf-code" maxlength="30" value="${esc(role ? role.roleCode : '')}" placeholder="如 CONTENT_REVIEWER"></label>
                <label class="field"><span>状态</span><select id="rf-status"><option value="1" ${!role || role.status === 1 ? 'selected' : ''}>启用</option><option value="0" ${role && role.status === 0 ? 'selected' : ''}>停用</option></select></label>
                <label class="field span-2"><span>备注</span><input id="rf-remark" maxlength="200" value="${esc(role ? (role.remark || '') : '')}"></label>
            </form>`,
            footer: '<button class="btn" data-close>取消</button><button class="btn btn-primary" id="role-save">保存</button>'
        });
        $('[data-close]').addEventListener('click', () => closeModal());
        $('#role-save').addEventListener('click', async (event) => {
            const roleName = $('#rf-name').value.trim();
            const roleCode = $('#rf-code').value.trim().toUpperCase();
            if (!roleName) return toast('请填写角色名称', 'error');
            if (!/^[A-Z][A-Z0-9_]{1,29}$/.test(roleCode)) return toast('角色编码需为大写字母开头的 2-30 位字母、数字或下划线', 'error');
            const payload = {
                roleName,
                roleCode,
                status: Number($('#rf-status').value),
                remark: $('#rf-remark').value.trim()
            };
            const button = event.currentTarget;
            setBusy(button, true);
            try {
                if (role) await api(`/api/roles/${role.id}`, {method: 'PUT', body: payload});
                else await api('/api/roles', {method: 'POST', body: payload});
                closeModal(true);
                state.rolesLoaded = false;
                toast('角色已保存', 'success');
                await renderRoles();
            } catch (error) {
                toast(error.message, 'error');
            } finally {
                setBusy(button, false);
            }
        });
    }

    function permissionGroups(selected = []) {
        const selectedSet = new Set(selected);
        const groups = {};
        state.permissions.forEach((perm) => {
            const prefix = perm.permCode.split(':')[0];
            (groups[prefix] = groups[prefix] || []).push(perm);
        });
        return Object.entries(groups).map(([prefix, perms]) => `
            <div class="perm-group">
                <h5>${esc(PERM_GROUP[prefix] || prefix)}</h5>
                <div class="perm-list">${perms.map((perm) => `
                    <label class="check-item"><input type="checkbox" name="perm" value="${perm.id}" ${selectedSet.has(perm.id) ? 'checked' : ''}>
                        <span>${esc(perm.permName)}<small>${esc(perm.permCode)}</small></span></label>`).join('')}</div>
            </div>`).join('');
    }

    function openRolePermissions(role) {
        openModal({
            title: '配置角色权限',
            subtitle: `${role.roleName} · ${role.roleCode}`,
            body: `<div class="toolbar" style="margin-bottom:12px">
                       <span class="muted small">已选择 <b id="perm-count">0</b> / ${state.permissions.length} 个权限点，保存后立即生效</span>
                       <button type="button" class="btn btn-sm" id="perm-toggle-all">全选 / 清空</button>
                   </div>
                   <div id="perm-groups">${permissionGroups(role.permissionIds || [])}</div>`,
            footer: '<button class="btn" data-close>取消</button><button class="btn btn-primary" id="perm-save">保存权限</button>'
        });
        const updatePermCount = () => {
            $('#perm-count').textContent = $$('input[name="perm"]:checked').length;
        };
        $$('input[name="perm"]').forEach((box) => box.addEventListener('change', updatePermCount));
        updatePermCount();
        $('[data-close]').addEventListener('click', () => closeModal());
        $('#perm-toggle-all').addEventListener('click', () => {
            const boxes = $$('input[name="perm"]');
            const allChecked = boxes.every((box) => box.checked);
            boxes.forEach((box) => { box.checked = !allChecked; });
            updatePermCount();
        });
        $('#perm-save').addEventListener('click', async (event) => {
            const permissionIds = $$('input[name="perm"]:checked').map((input) => Number(input.value));
            if (!(await confirmDialog({title: '保存角色权限', message: `将把角色的权限点调整为 ${permissionIds.length} 个，确定保存吗？`, confirmText: '保存'}))) return;
            const button = event.currentTarget;
            setBusy(button, true);
            try {
                await api(`/api/roles/${role.id}/permissions`, {method: 'PUT', body: {permissionIds}});
                closeModal(true);
                state.rolesLoaded = false;
                toast('角色权限已更新', 'success');
                await renderRoles();
            } catch (error) {
                toast(error.message, 'error');
            } finally {
                setBusy(button, false);
            }
        });
    }

    async function deleteRole(role) {
        if (!(await confirmDialog({title: '删除角色', message: `确定删除角色「${role.roleName}」吗？仍绑定用户时会被拒绝。`, confirmText: '删除', danger: true}))) return;
        try {
            await api(`/api/roles/${role.id}`, {method: 'DELETE'});
            state.rolesLoaded = false;
            toast('角色已删除', 'success');
            await renderRoles();
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    /* ------------------------------ 操作日志 ------------------------------ */
    async function renderLogs() {
        $('#content').innerHTML = `
            <form class="card filters" id="log-filters">
                <label class="field"><span>模块</span><input name="module" value="${esc(state.log.module)}" placeholder="如 文章管理"></label>
                <label class="field"><span>操作人</span><input name="username" value="${esc(state.log.username)}" placeholder="用户名"></label>
                <label class="field"><span>结果</span><select name="success"><option value="">全部</option><option value="1" ${state.log.success === '1' ? 'selected' : ''}>成功</option><option value="0" ${state.log.success === '0' ? 'selected' : ''}>失败</option></select></label>
                <label class="field"><span>开始日期</span><input type="date" name="startDate" value="${esc(state.log.startDate)}"></label>
                <label class="field"><span>结束日期</span><input type="date" name="endDate" value="${esc(state.log.endDate)}"></label>
                <div class="filter-actions"><button class="btn btn-primary" type="submit">筛选</button><button class="btn" type="button" id="reset-log-filter">重置</button><button class="btn" type="button" id="refresh-logs">↻ 刷新</button></div>
            </form>
            <div class="card">
                <div class="table-wrap"><table class="data-table">
                    <thead><tr><th>时间</th><th>操作人</th><th>模块 / 操作</th><th>请求</th><th>IP</th><th>耗时</th><th>结果</th><th>操作</th></tr></thead>
                    <tbody id="log-body"><tr><td colspan="8"><div class="empty-state">正在加载…</div></td></tr></tbody>
                </table></div>
                <div class="pagination" id="log-pager"></div>
            </div>`;
        $('#log-filters').addEventListener('submit', (event) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            state.log.module = String(form.get('module') || '').trim();
            state.log.username = String(form.get('username') || '').trim();
            state.log.success = form.get('success') || '';
            state.log.startDate = form.get('startDate') || '';
            state.log.endDate = form.get('endDate') || '';
            if (state.log.startDate && state.log.endDate && state.log.startDate > state.log.endDate) {
                toast('开始日期不能晚于结束日期', 'error');
                return;
            }
            state.log.pageNum = 1;
            loadLogs();
        });
        $('#reset-log-filter').addEventListener('click', () => {
            state.log = {...state.log, pageNum: 1, module: '', username: '', success: '', startDate: '', endDate: ''};
            renderLogs();
        });
        $('#refresh-logs').addEventListener('click', () => loadLogs());
        $('#log-body').addEventListener('click', (event) => {
            const button = event.target.closest('button[data-log-id]');
            if (!button) return;
            const log = state.logRows.find((item) => item.id === Number(button.dataset.logId));
            if (log) openLogDetail(log);
        });
        await loadLogs();
    }

    async function loadLogs() {
        try {
            const page = await api('/api/logs?' + qs(state.log));
            state.logRows = page.records || [];
            const body = $('#log-body');
            if (!page.records.length) {
                body.innerHTML = '<tr><td colspan="8"><div class="empty-state"><span class="empty-icon">◷</span><strong>没有匹配的操作日志</strong>调整模块、操作人或时间范围后重试</div></td></tr>';
            } else {
                body.innerHTML = page.records.map((log) => `
                    <tr>
                        <td class="cell-time">${fmt(log.createTime)}</td>
                        <td><b>${esc(log.username || 'anonymous')}</b><div class="small muted">#${log.userId || '—'}</div></td>
                        <td><div>${esc(log.module || '—')}</div><div class="small muted">${esc(log.operation || '—')}</div></td>
                        <td><div class="small">${esc(log.requestMethod || '')}</div><div class="small muted" style="max-width:240px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(log.requestUri || '—')}</div></td>
                        <td class="cell-time">${esc(log.ip || '—')}</td>
                        <td class="cell-time">${log.costTime == null ? '—' : log.costTime + ' ms'}</td>
                        <td>${log.success === 1 ? '<span class="badge badge-published">成功</span>' : '<span class="badge badge-rejected">失败</span>'}</td>
                        <td><button class="btn-link" data-log-id="${log.id}">详情</button></td>
                    </tr>`).join('');
            }
            $('#log-pager').innerHTML = pagerHtml(page, '条日志', {sizes: [10, 20, 50], pageSize: state.log.pageSize});
            bindPager($('#log-pager'), page, (next) => {
                state.log.pageNum = next;
                loadLogs();
            }, (size) => {
                state.log.pageSize = size;
                state.log.pageNum = 1;
                loadLogs();
            });
        } catch (error) {
            $('#log-body').innerHTML = `<tr><td colspan="8"><div class="empty-state"><span class="empty-icon">⚠</span>${esc(error.message)}</div></td></tr>`;
            toast(error.message, 'error');
        }
    }

    function openLogDetail(log) {
        let params = log.params || '';
        try {
            params = JSON.stringify(JSON.parse(params), null, 2);
        } catch (error) { /* 保留原始文本 */ }
        openModal({
            title: '操作日志详情',
            subtitle: `${log.module || ''} · ${log.operation || ''}`,
            size: 'modal-wide',
            body: `<dl class="detail-meta">
                    <div><dt>操作人</dt><dd>${esc(log.username || 'anonymous')}（#${log.userId || '—'}）</dd></div>
                    <div><dt>时间</dt><dd>${fmt(log.createTime)}</dd></div>
                    <div><dt>请求</dt><dd>${esc(log.requestMethod || '')} ${esc(log.requestUri || '')}</dd></div>
                    <div><dt>目标方法</dt><dd>${esc(log.method || '—')}</dd></div>
                    <div><dt>IP / 耗时</dt><dd>${esc(log.ip || '—')} · ${log.costTime == null ? '—' : log.costTime + ' ms'}</dd></div>
                    <div><dt>结果</dt><dd>${log.success === 1 ? '成功' : '失败'}</dd></div>
                </dl>
                <h4 style="margin:0 0 8px;font-size:13px">请求参数</h4>
                <div class="code-block">${esc(params || '无参数')}</div>
                ${log.errorMsg ? `<h4 style="margin:16px 0 8px;font-size:13px">错误信息</h4><div class="code-block" style="color:#fecaca">${esc(log.errorMsg)}</div>` : ''}`,
            footer: '<button class="btn" data-close>关闭</button>'
        });
        $('[data-close]').addEventListener('click', () => closeModal());
    }

    /* ------------------------------ 登录绑定 ------------------------------ */
    const REMEMBER_KEY = 'atlas.cms.username';

    function showLoginError(message) {
        const box = $('#login-error');
        box.textContent = message;
        box.hidden = !message;
    }

    function showRegisterPane(show) {
        const registerForm = $('#register-form');
        if (!registerForm) return;
        $('#login-form').hidden = show;
        $('#demo-accounts').hidden = show;
        $('#login-switch').hidden = show;
        registerForm.hidden = !show;
        if (show) {
            setTimeout(() => $('#reg-username').focus(), 50);
        } else {
            const box = $('#register-error');
            if (box) {
                box.hidden = true;
                box.textContent = '';
            }
        }
    }

    $('#show-register')?.addEventListener('click', () => {
        showLoginError('');
        showRegisterPane(true);
    });
    $('#show-login')?.addEventListener('click', () => showRegisterPane(false));

    $('#register-form')?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const username = $('#reg-username').value.trim();
        const nickname = $('#reg-nickname').value.trim();
        const email = $('#reg-email').value.trim();
        const password = $('#reg-password').value;
        const box = $('#register-error');
        const fail = (message) => {
            box.textContent = message;
            box.hidden = false;
        };
        box.hidden = true;
        if (!/^[A-Za-z0-9_]{4,32}$/.test(username)) {
            return fail('用户名须为 4-32 位字母、数字或下划线');
        }
        if (nickname.length < 2 || nickname.length > 20) {
            return fail('昵称长度须为 2-20 个字符');
        }
        if (password.length < 8 || password.length > 32
                || !/[A-Za-z]/.test(password) || !/\d/.test(password)) {
            return fail('密码须为 8-32 位，且同时包含字母和数字');
        }
        if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            return fail('邮箱格式不正确');
        }
        const button = $('#register-button');
        setBusy(button, true, '提交中…');
        try {
            await api('/api/auth/register', {
                method: 'POST',
                auth: false,
                body: {username, nickname, email: email || null, password}
            });
            toast('注册成功，请等待管理员审批', 'success');
            $('#login-username').value = username;
            $('#reg-username').value = '';
            $('#reg-nickname').value = '';
            $('#reg-email').value = '';
            $('#reg-password').value = '';
            showRegisterPane(false);
        } catch (error) {
            fail(error.message);
        } finally {
            setBusy(button, false);
        }
    });

    $('#login-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const button = $('#login-button');
        const username = $('#login-username').value.trim();
        const password = $('#login-password').value;
        if (!username) {
            showLoginError('请输入用户名');
            $('#login-username').focus();
            return;
        }
        if (!password) {
            showLoginError('请输入密码');
            $('#login-password').focus();
            return;
        }
        showLoginError('');
        setBusy(button, true, '登录中…');
        try {
            state.me = await api('/api/auth/login', {
                method: 'POST',
                auth: false,
                body: {username, password}
            });
            if ($('#remember-username').checked) {
                localStorage.setItem(REMEMBER_KEY, username);
            } else {
                localStorage.removeItem(REMEMBER_KEY);
            }
            await enterApp();
            toast('登录成功，欢迎回来', 'success');
        } catch (error) {
            showLoginError(error.message);
        } finally {
            setBusy(button, false);
        }
    });

    $('#password-toggle').addEventListener('click', (event) => {
        const input = $('#login-password');
        const show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        event.currentTarget.textContent = show ? '隐藏' : '显示';
        event.currentTarget.setAttribute('aria-label', show ? '隐藏密码' : '显示密码');
        event.currentTarget.setAttribute('aria-pressed', String(show));
    });

    $$('.demo-accounts button').forEach((button) => {
        button.addEventListener('click', () => {
            $('#login-username').value = button.dataset.user;
            $('#login-password').value = button.dataset.pass;
            showLoginError('');
        });
    });

    $('#logout-button').addEventListener('click', async () => {
        const ok = await confirmDialog({title: '退出登录', message: '确定要退出当前账号吗？', confirmText: '退出'});
        if (!ok) return;
        try {
            await api('/api/auth/logout', {method: 'POST'});
        } catch (error) { /* 忽略退出异常 */ }
        state.me = null;
        history.replaceState(null, '', '#/dashboard');
        showLogin();
        toast('已退出登录');
    });

    $('#sidebar-toggle').addEventListener('click', () => {
        setSidebar(!$('#app').classList.contains('nav-open'));
    });
    $('#sidebar-backdrop').addEventListener('click', () => setSidebar(false));
    window.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') setSidebar(false);
    });
    window.addEventListener('online', probeService);
    window.addEventListener('offline', () => {
        $('#service-dot').classList.add('is-offline');
        $('#service-dot').classList.remove('is-pending');
        $('#service-status').textContent = '网络已断开';
    });

    const remembered = localStorage.getItem(REMEMBER_KEY);
    if (remembered) {
        $('#login-username').value = remembered;
        $('#remember-username').checked = true;
    }

    boot();
})();
