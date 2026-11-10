/* Atlas CMS 管理端（原生 JS，无外部依赖） */
(() => {
    'use strict';

    const STATUS_TEXT = {0: '草稿', 1: '待审核', 2: '审核通过', 3: '已驳回', 4: '已发布', 5: '已下架'};
    const STATUS_CLASS = {0: 'draft', 1: 'pending', 2: 'approved', 3: 'rejected', 4: 'published', 5: 'offline'};
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
        article: {pageNum: 1, pageSize: 8, title: '', categoryId: '', status: '', startDate: '', endDate: '', sort: 'createTime|desc'},
        articleRows: [],
        selectedArticles: new Set(),
        user: {pageNum: 1, pageSize: 10, keyword: '', status: ''},
        log: {pageNum: 1, pageSize: 10, module: '', username: '', success: '', startDate: '', endDate: ''}
    };

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
        node.textContent = message;
        root.appendChild(node);
        setTimeout(() => node.remove(), 3200);
    }

    function handleUnauthorized() {
        if (!state.me) return;
        state.me = null;
        closeModal();
        showLogin();
        toast('登录状态已失效，请重新登录', 'error');
    }

    async function api(path, options = {}) {
        const {method = 'GET', body, auth = true} = options;
        const headers = {Accept: 'application/json'};
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        let response;
        try {
            response = await fetch(path, {
                method,
                headers,
                credentials: 'same-origin',
                body: body === undefined ? undefined : JSON.stringify(body)
            });
        } catch (error) {
            throw new Error('网络请求失败，请确认服务已启动');
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

    function showLogin(clearPassword = true) {
        if (clearPassword) $('#login-password').value = '';
        $('#app').classList.add('hidden');
        $('#login-screen').classList.remove('hidden');
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
    }

    function renderNav() {
        const nav = $('#nav');
        nav.innerHTML = NAV.filter((item) => !item.perm || has(item.perm)).map((item) =>
            `<button type="button" data-page="${item.id}" class="${state.page === item.id ? 'active' : ''}">
                <span class="ico">${item.icon}</span><span>${item.label}</span>
            </button>`).join('');
        $$('#nav button').forEach((button) => {
            button.addEventListener('click', () => navigate(button.dataset.page));
        });
    }

    function navigate(page) {
        const target = NAV.find((item) => item.id === page);
        if (!target || (target.perm && !has(target.perm))) {
            toast('没有访问该模块的权限', 'error');
            return;
        }
        state.page = page;
        renderNav();
        const meta = PAGE_META[page];
        $('#page-kicker').textContent = meta[0];
        $('#page-title').textContent = meta[1];
        $('#content').innerHTML = '<div class="card card-pad muted">正在加载…</div>';
        const renderers = {
            dashboard: renderDashboard,
            articles: renderArticles,
            categories: renderCategories,
            users: renderUsers,
            roles: renderRoles,
            logs: renderLogs
        };
        renderers[page]().catch((error) => {
            $('#content').innerHTML = `<div class="card"><div class="empty-state"><strong>加载失败</strong>${esc(error.message)}</div></div>`;
        });
    }

    async function boot() {
        try {
            state.me = await api('/api/auth/me', {auth: false});
            await enterApp();
        } catch (error) {
            showLogin(false);
        }
    }

    async function enterApp() {
        showApp();
        state.page = 'dashboard';
        renderNav();
        if (has('category:list')) {
            try {
                state.allTree = await api('/api/categories/tree');
            } catch (error) {
                state.allTree = [];
            }
        }
        navigate('dashboard');
    }

    /* ------------------------------ 弹窗 ------------------------------ */
    function openModal({title, subtitle = '', body, footer = '', size = '', onMount}) {
        closeModal();
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
        $('.modal-close', root).addEventListener('click', closeModal);
        $('.modal-backdrop', root).addEventListener('mousedown', (event) => {
            if (event.target === event.currentTarget) closeModal();
        });
        if (onMount) onMount(root);
    }

    function closeModal() {
        $('#modal-root').innerHTML = '';
        document.body.style.overflow = '';
    }

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') closeModal();
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

    function pagerHtml(page, label = '条') {
        return `<div class="pagination-info">第 ${page.pageNum} / ${Math.max(page.pages, 1)} 页 · 共 ${page.total} ${label}</div>
            <div class="pagination-actions">
                <button type="button" class="btn btn-sm" data-page="${page.pageNum - 1}" ${page.pageNum <= 1 ? 'disabled' : ''}>上一页</button>
                <button type="button" class="btn btn-sm" data-page="${page.pageNum + 1}" ${page.pageNum >= page.pages ? 'disabled' : ''}>下一页</button>
            </div>`;
    }

    function bindPager(container, page, onPage) {
        $$('button[data-page]', container).forEach((button) => {
            button.addEventListener('click', () => {
                const next = Number(button.dataset.page);
                if (next >= 1 && next <= page.pages) onPage(next);
            });
        });
    }

    /* ------------------------------ 工作台 ------------------------------ */
    async function renderDashboard() {
        const [allPage, pendingPage, publishedPage] = await Promise.all([
            api('/api/articles?pageNum=1&pageSize=5&sortField=createTime&sortOrder=desc'),
            api('/api/articles?pageNum=1&pageSize=1&status=1'),
            api('/api/articles?pageNum=1&pageSize=1&status=4')
        ]);
        const categories = flattenTree(state.allTree);
        const recent = allPage.records || [];
        const name = esc(state.me.nickname || state.me.username);
        const roleNames = state.me.roles.map((role) => role === 'ADMIN' ? '超级管理员' : role === 'EDITOR' ? '内容编辑' : role === 'AUTHOR' ? '内容作者' : role === 'VIEWER' ? '只读访客' : role).join(' / ');

        $('#content').innerHTML = `
            <section class="hero-card">
                <h3>你好，${name}</h3>
                <p>今天也请让内容保持有序：从分类规划、草稿撰写，到审核发布与操作审计，所有动作都在同一套权限边界内完成。</p>
                <div class="hero-meta"><span>当前身份：${esc(roleNames)}</span><span>权限点：${state.me.permissions.length} 项</span><span>系统状态：运行中</span></div>
            </section>
            <section class="stat-grid">
                <div class="stat-card"><div class="stat-label">全部文章</div><div class="stat-value">${allPage.total}</div><div class="stat-foot">覆盖草稿、审核与发布全状态</div></div>
                <div class="stat-card"><div class="stat-label">待审核</div><div class="stat-value">${pendingPage.total}</div><div class="stat-foot">${has('article:audit') ? '等待你给出审核结论' : '由内容编辑处理'}</div></div>
                <div class="stat-card"><div class="stat-label">已发布</div><div class="stat-value">${publishedPage.total}</div><div class="stat-foot">线上可见内容</div></div>
                <div class="stat-card"><div class="stat-label">分类节点</div><div class="stat-value">${categories.length}</div><div class="stat-foot">多级分类树节点总数</div></div>
            </section>
            <section class="dashboard-grid">
                <div class="card">
                    <div class="section-title"><h4>最近内容</h4><button class="btn-link" data-go="articles">查看全部 →</button></div>
                    <div class="table-wrap">
                        <table class="data-table">
                            <thead><tr><th>标题</th><th>分类</th><th>状态</th><th>更新时间</th></tr></thead>
                            <tbody>
                            ${recent.length ? recent.map((article) => `
                                <tr>
                                    <td class="title-cell"><span class="title-main">${esc(article.title)}</span><span class="title-sub">${esc(article.authorName || '—')}</span></td>
                                    <td>${esc(article.categoryName || '—')}</td>
                                    <td>${articleBadge(article.status)}</td>
                                    <td class="cell-time">${fmt(article.updateTime)}</td>
                                </tr>`).join('') : '<tr><td colspan="4"><div class="empty-state">暂无内容</div></td></tr>'}
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
        $$('[data-go]', $('#content')).forEach((button) => {
            button.addEventListener('click', () => navigate(button.dataset.go));
        });
    }

    /* ------------------------------ 文章 ------------------------------ */
    async function renderArticles() {
        if (!state.allTree.length) {
            try { state.allTree = await api('/api/categories/tree'); } catch (error) { /* ignore */ }
        }
        const flatCategories = flattenTree(state.allTree);
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
                <label class="field"><span>标题关键字</span><input name="title" value="${esc(state.article.title)}" placeholder="模糊搜索标题"></label>
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
            <div class="card">
                <div class="table-wrap">
                    <table class="data-table">
                        <thead><tr>
                            <th style="width:38px"><input type="checkbox" id="article-check-all" title="全选本页"></th>
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
            state.article.title = String(form.get('title') || '').trim();
            state.article.categoryId = form.get('categoryId') || '';
            state.article.status = form.get('status') || '';
            state.article.startDate = form.get('startDate') || '';
            state.article.endDate = form.get('endDate') || '';
            state.article.sort = form.get('sort') || 'createTime|desc';
            state.article.pageNum = 1;
            loadArticles();
        });
        $('#reset-article-filter').addEventListener('click', () => {
            state.article = {...state.article, pageNum: 1, title: '', categoryId: '', status: '', startDate: '', endDate: '', sort: 'createTime|desc'};
            renderArticles();
        });
        $('#refresh-articles').addEventListener('click', () => loadArticles());
        $('#new-article')?.addEventListener('click', () => openArticleForm());
        $('#batch-publish')?.addEventListener('click', () => batchChange('publish'));
        $('#batch-offline')?.addEventListener('click', () => batchChange('offline'));
        $('#article-check-all').addEventListener('change', (event) => {
            $$('.row-check', $('#article-body')).forEach((checkbox) => {
                checkbox.checked = event.target.checked;
                if (checkbox.checked) state.selectedArticles.add(Number(checkbox.value));
                else state.selectedArticles.delete(Number(checkbox.value));
            });
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
        });
        await loadArticles();
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
        try {
            const page = await api('/api/articles?' + articleQuery());
            state.articleRows = page.records || [];
            const body = $('#article-body');
            if (!state.articleRows.length) {
                body.innerHTML = '<tr><td colspan="7"><div class="empty-state"><strong>没有匹配的文章</strong>调整筛选条件或新建一篇内容</div></td></tr>';
            } else {
                body.innerHTML = state.articleRows.map((article) => {
                    const owner = article.authorId === state.me.id;
                    const editableStatus = article.status === 0 || article.status === 3;
                    const canWrite = has('article:edit') && (isManager() || owner) && editableStatus;
                    const canSubmit = has('article:submit') && (isManager() || owner) && editableStatus;
                    const canDelete = has('article:delete') && (isManager() || owner) && (isManager() || editableStatus);
                    const actions = [`<button class="btn-link" data-action="detail" data-id="${article.id}">详情</button>`];
                    if (canWrite) actions.push(`<button class="btn-link" data-action="edit" data-id="${article.id}">编辑</button>`);
                    if (canSubmit) actions.push(`<button class="btn-link" data-action="submit" data-id="${article.id}">提交审核</button>`);
                    if (article.status === 1 && has('article:audit')) actions.push(`<button class="btn-link" data-action="audit" data-id="${article.id}">审核</button>`);
                    if ((article.status === 2 || article.status === 5) && has('article:publish')) actions.push(`<button class="btn-link" data-action="publish" data-id="${article.id}">发布</button>`);
                    if (article.status === 4 && has('article:offline')) actions.push(`<button class="btn-link" data-action="offline" data-id="${article.id}">下架</button>`);
                    if (canDelete) actions.push(`<button class="btn-link danger" data-action="delete" data-id="${article.id}">删除</button>`);
                    return `<tr>
                        <td><input class="row-check" type="checkbox" value="${article.id}"></td>
                        <td class="title-cell"><span class="title-main" title="${esc(article.title)}">${esc(article.title)}</span><span class="title-sub">v${article.version} · 阅读 ${article.viewCount || 0}</span></td>
                        <td><div>${esc(article.categoryName || '—')}</div><div class="small muted">${esc(article.authorName || '—')}</div></td>
                        <td>${articleBadge(article.status)}${article.auditRemark ? `<div class="small muted" style="max-width:160px;white-space:normal">${esc(article.auditRemark)}</div>` : ''}</td>
                        <td class="cell-time">${fmt(article.publishTime)}</td>
                        <td class="cell-time">${fmt(article.updateTime)}</td>
                        <td><div class="action-group">${actions.join('')}</div></td>
                    </tr>`;
                }).join('');
            }
            $('#article-pager').innerHTML = pagerHtml(page, '篇文章');
            bindPager($('#article-pager'), page, (next) => {
                state.article.pageNum = next;
                loadArticles();
            });
        } catch (error) {
            $('#article-body').innerHTML = `<tr><td colspan="7"><div class="empty-state">${esc(error.message)}</div></td></tr>`;
            toast(error.message, 'error');
        }
    }

    async function handleArticleAction(action, id) {
        try {
            if (action === 'detail') return openArticleDetail(id);
            if (action === 'edit') return openArticleForm(id);
            if (action === 'audit') return openAuditModal(id);
            if (action === 'delete') {
                if (!confirm('确定删除这篇文章吗？删除后不可在列表中恢复。')) return;
                await api(`/api/articles/${id}`, {method: 'DELETE'});
                toast('文章已删除', 'success');
                await loadArticles();
                return;
            }
            if (action === 'submit') {
                if (!confirm('提交审核后，文章将进入待审核队列且不可编辑，确定继续吗？')) return;
                await api(`/api/articles/${id}/submit`, {method: 'POST'});
                toast('已提交审核', 'success');
                await loadArticles();
                return;
            }
            if (action === 'publish' || action === 'offline') {
                const label = action === 'publish' ? '发布' : '下架';
                if (!confirm(`确定${label}这篇文章吗？`)) return;
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
        if (!confirm(`确定批量${label}选中的 ${ids.length} 篇文章吗？`)) return;
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
                    </dl>
                    ${article.summary ? `<p class="muted" style="line-height:1.7">${esc(article.summary)}</p>` : ''}
                    <div class="detail-content">${esc(article.content)}</div>`,
                footer: '<button class="btn" data-close>关闭</button>'
            });
            $('[data-close]').addEventListener('click', closeModal);
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    async function openArticleForm(id) {
        try {
            const tree = await api('/api/categories/tree?enabledOnly=true');
            const article = id ? await api(`/api/articles/${id}`) : null;
            if (article && article.status !== 0 && article.status !== 3) {
                toast('只有草稿或已驳回文章可编辑', 'error');
                return;
            }
            const options = categoryOptions(tree, article ? article.categoryId : '');
            if (!options) {
                toast('暂无启用的分类，请先创建分类', 'error');
                return;
            }
            openModal({
                title: article ? '编辑文章' : '新建文章',
                subtitle: article ? `当前状态：${STATUS_TEXT[article.status]} · 保存使用乐观锁校验版本` : '保存为草稿，或直接提交审核',
                size: 'modal-lg',
                body: `
                    <form id="article-form" class="form-grid">
                        <label class="field span-2"><span>标题 *</span><input id="af-title" maxlength="100" value="${esc(article ? article.title : '')}" placeholder="2-100 个字符"></label>
                        <label class="field"><span>分类 *</span><select id="af-category"><option value="">请选择分类</option>${options}</select></label>
                        <label class="field"><span>摘要</span><input id="af-summary" maxlength="500" value="${esc(article ? (article.summary || '') : '')}" placeholder="选填，最多 500 字"></label>
                        <label class="field span-2"><span>正文 *</span><textarea id="af-content" style="min-height:240px" placeholder="至少 10 个字符">${esc(article ? article.content : '')}</textarea></label>
                    </form>`,
                footer: `
                    <button class="btn" data-close>取消</button>
                    <button class="btn" id="save-draft">保存草稿</button>
                    <button class="btn btn-primary" id="save-submit">保存并提交审核</button>`
            });
            $('[data-close]').addEventListener('click', closeModal);
            const save = async (submitForReview) => {
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
                try {
                    if (article) {
                        payload.version = article.version;
                        await api(`/api/articles/${article.id}`, {method: 'PUT', body: payload});
                    } else {
                        await api('/api/articles', {method: 'POST', body: payload});
                    }
                    closeModal();
                    toast(submitForReview ? '已保存并提交审核' : '已保存草稿', 'success');
                    await loadArticles();
                } catch (error) {
                    toast(error.message, 'error');
                }
            };
            $('#save-draft').addEventListener('click', () => save(false));
            $('#save-submit').addEventListener('click', () => save(true));
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    async function openAuditModal(id) {
        try {
            const article = await api(`/api/articles/${id}`);
            openModal({
                title: '审核文章',
                subtitle: article.title,
                body: `
                    <div class="detail-meta">
                        <div><dt>分类</dt><dd>${esc(article.categoryName || '—')}</dd></div>
                        <div><dt>作者</dt><dd>${esc(article.authorName || '—')}</dd></div>
                        <div><dt>提交时间</dt><dd>${fmt(article.updateTime)}</dd></div>
                        <div><dt>状态</dt><dd>${articleBadge(article.status)}</dd></div>
                    </div>
                    <p class="muted" style="line-height:1.7">${esc(article.summary || '无摘要')}</p>
                    <div class="detail-content" style="max-height:240px;overflow:auto;padding:14px;background:#f8fafc;border-radius:10px">${esc(article.content)}</div>
                    <div class="field" style="margin-top:18px"><span>审核结论 *</span>
                        <div class="check-list">
                            <label class="check-item"><input type="radio" name="audit-result" value="approve" checked> 审核通过</label>
                            <label class="check-item"><input type="radio" name="audit-result" value="reject"> 驳回修改</label>
                        </div>
                    </div>
                    <label class="field"><span>审核意见</span><textarea id="audit-remark" maxlength="500" placeholder="驳回时必须填写审核意见"></textarea></label>`,
                footer: '<button class="btn" data-close>取消</button><button class="btn btn-primary" id="audit-submit">提交审核结论</button>'
            });
            $('[data-close]').addEventListener('click', closeModal);
            $('#audit-submit').addEventListener('click', async () => {
                const approved = $('input[name="audit-result"]:checked').value === 'approve';
                const remark = $('#audit-remark').value.trim();
                if (!approved && !remark) return toast('驳回时必须填写审核意见', 'error');
                try {
                    await api(`/api/articles/${id}/audit`, {method: 'POST', body: {approved, remark}});
                    closeModal();
                    toast(approved ? '审核通过' : '已驳回', 'success');
                    await loadArticles();
                } catch (error) {
                    toast(error.message, 'error');
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
        $('#content').innerHTML = `
            <div class="toolbar">
                <div class="toolbar-actions">${manage ? '<button class="btn btn-primary" id="new-category">＋ 新建顶级分类</button>' : ''}</div>
                <span class="muted small">共 ${rows.length} 个分类节点 · 停用分类不可挂载新文章</span>
            </div>
            <div class="card">
                <div class="table-wrap"><table class="data-table">
                    <thead><tr><th>分类名称</th><th>排序</th><th>状态</th><th>描述</th><th>创建时间</th>${manage ? '<th style="width:260px">操作</th>' : ''}</tr></thead>
                    <tbody>
                    ${rows.length ? rows.map((node) => `
                        <tr class="category-tree-row">
                            <td><div class="tree-name" style="padding-left:${node._depth * 24}px">${node._depth ? '<span class="tree-branch">└</span>' : ''}<b>${esc(node.name)}</b>${node.children && node.children.length ? `<span class="tag">${node.children.length} 子级</span>` : ''}</div></td>
                            <td>${node.sort}</td>
                            <td>${node.status === 1 ? '<span class="badge badge-published">启用</span>' : '<span class="badge badge-offline">停用</span>'}</td>
                            <td class="muted">${esc(node.description || '—')}</td>
                            <td class="cell-time">${fmt(node.createTime)}</td>
                            ${manage ? `<td><div class="action-group">
                                <button class="btn-link" data-cat-action="child" data-id="${node.id}">添加子级</button>
                                <button class="btn-link" data-cat-action="edit" data-id="${node.id}">编辑</button>
                                <button class="btn-link" data-cat-action="toggle" data-id="${node.id}">${node.status === 1 ? '停用' : '启用'}</button>
                                <button class="btn-link danger" data-cat-action="delete" data-id="${node.id}">删除</button>
                            </div></td>` : ''}
                        </tr>`).join('') : '<tr><td colspan="6"><div class="empty-state">暂无分类</div></td></tr>'}
                    </tbody>
                </table></div>
            </div>`;
        $('#new-category')?.addEventListener('click', () => openCategoryForm(null, 0));
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
            subtitle: '分类支持多级树结构，停用后不可被新文章挂载',
            body: `<form class="form-grid">
                <label class="field span-2"><span>分类名称 *</span><input id="cf-name" maxlength="50" value="${esc(node ? node.name : '')}" placeholder="2-50 个字符"></label>
                <label class="field"><span>父分类</span><select id="cf-parent"><option value="0">（顶级分类）</option>${options}</select></label>
                <label class="field"><span>排序值</span><input id="cf-sort" type="number" min="0" max="9999" value="${node ? node.sort : 0}"></label>
                <label class="field"><span>状态</span><select id="cf-status"><option value="1" ${!node || node.status === 1 ? 'selected' : ''}>启用</option><option value="0" ${node && node.status === 0 ? 'selected' : ''}>停用</option></select></label>
                <label class="field span-2"><span>描述</span><input id="cf-desc" maxlength="200" value="${esc(node ? (node.description || '') : '')}"></label>
            </form>`,
            footer: '<button class="btn" data-close>取消</button><button class="btn btn-primary" id="category-save">保存</button>'
        });
        $('[data-close]').addEventListener('click', closeModal);
        $('#category-save').addEventListener('click', async () => {
            const name = $('#cf-name').value.trim();
            if (name.length < 2) return toast('分类名称至少 2 个字符', 'error');
            const payload = {
                name,
                parentId: Number($('#cf-parent').value),
                sort: Number($('#cf-sort').value || 0),
                status: Number($('#cf-status').value),
                description: $('#cf-desc').value.trim()
            };
            try {
                if (node) await api(`/api/categories/${node.id}`, {method: 'PUT', body: payload});
                else await api('/api/categories', {method: 'POST', body: payload});
                closeModal();
                toast('分类已保存', 'success');
                await renderCategories();
            } catch (error) {
                toast(error.message, 'error');
            }
        });
    }

    async function toggleCategory(node) {
        const next = node.status === 1 ? 0 : 1;
        if (!confirm(`确定${next === 1 ? '启用' : '停用'}分类「${node.name}」吗？`)) return;
        try {
            await api(`/api/categories/${node.id}/status`, {method: 'PUT', body: {status: next}});
            toast('分类状态已更新', 'success');
            await renderCategories();
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    async function deleteCategory(node) {
        if (!confirm(`确定删除分类「${node.name}」吗？存在子分类或文章时将被拒绝。`)) return;
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
                <span class="muted small">注册用户默认为“待审批”，启用后方可登录</span>
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
                body.innerHTML = '<tr><td colspan="6"><div class="empty-state">没有匹配的用户</div></td></tr>';
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
        $('[data-close]').addEventListener('click', closeModal);
        $('#user-save').addEventListener('click', async () => {
            const roleIds = $$('input[name="user-role"]:checked').map((input) => Number(input.value));
            const payload = {
                username: $('#uf-username').value.trim(),
                password: $('#uf-password').value,
                nickname: $('#uf-nickname').value.trim(),
                email: $('#uf-email').value.trim(),
                status: Number($('#uf-status').value),
                roleIds
            };
            try {
                await api('/api/users', {method: 'POST', body: payload});
                closeModal();
                toast('用户创建成功', 'success');
                await loadUsers();
            } catch (error) {
                toast(error.message, 'error');
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
                if (!confirm(`确定${label}用户「${row.nickname}」吗？`)) return;
                await api(`/api/users/${id}/status`, {method: 'PUT', body: {status}});
                toast(`用户已${label}`, 'success');
                await loadUsers();
                return;
            }
            if (action === 'roles') return openUserRoles(row);
            if (action === 'password') return openResetPassword(row);
            if (action === 'delete') {
                if (!confirm(`确定删除用户「${row.nickname}」吗？`)) return;
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
        $('[data-close]').addEventListener('click', closeModal);
        $('#roles-save').addEventListener('click', async () => {
            const roleIds = $$('input[name="user-role"]:checked').map((input) => Number(input.value));
            try {
                await api(`/api/users/${user.id}/roles`, {method: 'PUT', body: {roleIds}});
                closeModal();
                toast('角色已更新', 'success');
                await loadUsers();
            } catch (error) {
                toast(error.message, 'error');
            }
        });
    }

    function openResetPassword(user) {
        openModal({
            title: '重置密码',
            subtitle: `${user.nickname}（@${user.username}）`,
            body: `<label class="field"><span>新密码 *</span><input id="rp-password" type="password" maxlength="32" placeholder="8-32 位，含字母和数字"></label>`,
            footer: '<button class="btn" data-close>取消</button><button class="btn btn-primary" id="rp-save">重置</button>'
        });
        $('[data-close]').addEventListener('click', closeModal);
        $('#rp-save').addEventListener('click', async () => {
            try {
                await api(`/api/users/${user.id}/password`, {method: 'PUT', body: {newPassword: $('#rp-password').value}});
                closeModal();
                toast('密码已重置', 'success');
            } catch (error) {
                toast(error.message, 'error');
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
                <span class="muted small">权限码通过 Spring AOP 在接口层校验</span>
            </div>
            <div class="role-grid">
                ${roles.map((role) => `
                    <div class="role-card">
                        <div class="role-card-head">
                            <div><h4>${esc(role.roleName)}</h4><span class="role-code">${esc(role.roleCode)}</span></div>
                            ${role.status === 1 ? '<span class="badge badge-published">启用</span>' : '<span class="badge badge-offline">停用</span>'}
                        </div>
                        <p class="role-desc">${esc(role.remark || '暂无备注')}</p>
                        <div class="role-foot">
                            <span class="muted small">${(role.permissionIds || []).length} 个权限点</span>
                            <div class="action-group">
                                <button class="btn-link" data-role-action="edit" data-id="${role.id}">编辑</button>
                                <button class="btn-link" data-role-action="perms" data-id="${role.id}">配置权限</button>
                                ${role.roleCode === 'ADMIN' ? '' : `<button class="btn-link danger" data-role-action="delete" data-id="${role.id}">删除</button>`}
                            </div>
                        </div>
                    </div>`).join('')}
            </div>`;
        $('#new-role').addEventListener('click', () => openRoleForm(null));
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
        $('[data-close]').addEventListener('click', closeModal);
        $('#role-save').addEventListener('click', async () => {
            const payload = {
                roleName: $('#rf-name').value.trim(),
                roleCode: $('#rf-code').value.trim().toUpperCase(),
                status: Number($('#rf-status').value),
                remark: $('#rf-remark').value.trim()
            };
            try {
                if (role) await api(`/api/roles/${role.id}`, {method: 'PUT', body: payload});
                else await api('/api/roles', {method: 'POST', body: payload});
                closeModal();
                state.rolesLoaded = false;
                toast('角色已保存', 'success');
                await renderRoles();
            } catch (error) {
                toast(error.message, 'error');
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
            body: `<div class="toolbar" style="margin-bottom:12px"><span class="muted small">勾选后立即生效，相关用户下次请求时重新加载</span><button type="button" class="btn btn-sm" id="perm-toggle-all">全选 / 清空</button></div>
                   <div id="perm-groups">${permissionGroups(role.permissionIds || [])}</div>`,
            footer: '<button class="btn" data-close>取消</button><button class="btn btn-primary" id="perm-save">保存权限</button>'
        });
        $('[data-close]').addEventListener('click', closeModal);
        $('#perm-toggle-all').addEventListener('click', () => {
            const boxes = $$('input[name="perm"]');
            const allChecked = boxes.every((box) => box.checked);
            boxes.forEach((box) => { box.checked = !allChecked; });
        });
        $('#perm-save').addEventListener('click', async () => {
            const permissionIds = $$('input[name="perm"]:checked').map((input) => Number(input.value));
            try {
                await api(`/api/roles/${role.id}/permissions`, {method: 'PUT', body: {permissionIds}});
                closeModal();
                state.rolesLoaded = false;
                toast('角色权限已更新', 'success');
                await renderRoles();
            } catch (error) {
                toast(error.message, 'error');
            }
        });
    }

    async function deleteRole(role) {
        if (!confirm(`确定删除角色「${role.roleName}」吗？仍绑定用户时会被拒绝。`)) return;
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
                <div class="filter-actions"><button class="btn btn-primary" type="submit">筛选</button><button class="btn" type="button" id="reset-log-filter">重置</button></div>
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
            state.log.pageNum = 1;
            loadLogs();
        });
        $('#reset-log-filter').addEventListener('click', () => {
            state.log = {...state.log, pageNum: 1, module: '', username: '', success: '', startDate: '', endDate: ''};
            renderLogs();
        });
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
                body.innerHTML = '<tr><td colspan="8"><div class="empty-state">没有匹配的操作日志</div></td></tr>';
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
            $('#log-pager').innerHTML = pagerHtml(page, '条日志');
            bindPager($('#log-pager'), page, (next) => {
                state.log.pageNum = next;
                loadLogs();
            });
        } catch (error) {
            $('#log-body').innerHTML = `<tr><td colspan="8"><div class="empty-state">${esc(error.message)}</div></td></tr>`;
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
        $('[data-close]').addEventListener('click', closeModal);
    }

    /* ------------------------------ 登录绑定 ------------------------------ */
    $('#login-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const button = $('#login-button');
        button.disabled = true;
        button.textContent = '登录中…';
        try {
            state.me = await api('/api/auth/login', {
                method: 'POST',
                auth: false,
                body: {
                    username: $('#login-username').value.trim(),
                    password: $('#login-password').value
                }
            });
            await enterApp();
            toast('登录成功，欢迎回来', 'success');
        } catch (error) {
            toast(error.message, 'error');
        } finally {
            button.disabled = false;
            button.textContent = '登 录';
        }
    });

    $$('.demo-accounts button').forEach((button) => {
        button.addEventListener('click', () => {
            $('#login-username').value = button.dataset.user;
            $('#login-password').value = button.dataset.pass;
        });
    });

    $('#logout-button').addEventListener('click', async () => {
        try {
            await api('/api/auth/logout', {method: 'POST'});
        } catch (error) { /* 忽略退出异常 */ }
        state.me = null;
        showLogin();
        toast('已退出登录');
    });

    boot();
})();
