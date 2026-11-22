/* Atlas CMS 读者端：只调用 /api/public 下的匿名只读接口 */
(() => {
    'use strict';

    const API_BASE = (() => {
        const path = window.location.pathname;
        return path.replace(/[^/]*$/, '') + 'api/public/';
    })();

    const state = {
        categoryId: '',
        title: '',
        pageNum: 1,
        pageSize: 6,
        categories: []
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

    async function api(path) {
        const response = await fetch(API_BASE + path, {
            headers: {Accept: 'application/json'}
        });
        let body = null;
        try {
            body = await response.json();
        } catch (error) {
            /* 非 JSON 响应按错误处理 */
        }
        if (!response.ok) {
            throw new Error(body && body.message ? body.message : `请求失败（${response.status}）`);
        }
        return body ? body.data : null;
    }

    function fmt(value) {
        return value ? String(value) : '—';
    }

    async function loadCategories() {
        try {
            state.categories = (await api('categories/tree')) || [];
            renderCategories();
        } catch (error) {
            $('#reader-categories').innerHTML = `<p class="reader-empty">${esc(error.message)}</p>`;
        }
    }

    function renderCategories() {
        const items = [
            `<button class="cat-item ${state.categoryId === '' ? 'active' : ''}" data-cat="">全部文章</button>`
        ];
        const walk = (nodes, depth) => {
            (nodes || []).forEach((node) => {
                const active = String(state.categoryId) === String(node.id);
                items.push(`<button class="cat-item ${active ? 'active' : ''}" data-cat="${node.id}" style="padding-left:${12 + depth * 14}px">${esc(node.name)}</button>`);
                walk(node.children, depth + 1);
            });
        };
        walk(state.categories, 0);
        $('#reader-categories').innerHTML = items.join('');
        $$('.cat-item').forEach((button) => {
            button.addEventListener('click', () => {
                state.categoryId = button.dataset.cat;
                state.pageNum = 1;
                renderCategories();
                loadArticles();
            });
        });
    }

    async function loadArticles() {
        const list = $('#reader-articles');
        list.innerHTML = '<div class="reader-loading">正在加载文章…</div>';
        try {
            const page = await api('articles?' + qs({
                pageNum: state.pageNum,
                pageSize: state.pageSize,
                title: state.title,
                categoryId: state.categoryId,
                sortField: 'publishTime',
                sortOrder: 'desc'
            }));
            const records = page ? page.records : [];
            $('#reader-list-count').textContent = `共 ${page ? page.total : 0} 篇`;
            if (!records.length) {
                list.innerHTML = '<div class="reader-empty">暂时没有符合条件的文章。</div>';
            } else {
                list.innerHTML = records.map((article) => `
                    <button class="article-card" data-id="${article.id}">
                        <span class="article-card-cat">${esc(article.categoryName || '未分类')}</span>
                        <strong>${esc(article.title)}</strong>
                        <p>${esc(article.summary || '暂无摘要')}</p>
                        <span class="article-card-meta">${esc(article.authorName || '匿名作者')} · ${fmt(article.publishTime)} · 阅读 ${article.viewCount || 0}</span>
                    </button>`).join('');
                $$('.article-card', list).forEach((card) => {
                    card.addEventListener('click', () => openArticle(Number(card.dataset.id)));
                });
            }
            renderPager(page);
        } catch (error) {
            list.innerHTML = `<div class="reader-empty">${esc(error.message)}</div>`;
        }
    }

    function renderPager(page) {
        const pager = $('#reader-pager');
        if (!page || page.pages <= 1) {
            pager.innerHTML = '';
            return;
        }
        pager.innerHTML = `
            <button type="button" id="prev-page" ${page.pageNum <= 1 ? 'disabled' : ''}>上一页</button>
            <span>${page.pageNum} / ${page.pages}</span>
            <button type="button" id="next-page" ${page.pageNum >= page.pages ? 'disabled' : ''}>下一页</button>`;
        $('#prev-page')?.addEventListener('click', () => {
            state.pageNum = Math.max(1, state.pageNum - 1);
            loadArticles();
        });
        $('#next-page')?.addEventListener('click', () => {
            state.pageNum = Math.min(page.pages, state.pageNum + 1);
            loadArticles();
        });
    }

    async function openArticle(id) {
        try {
            const article = await api(`articles/${id}`);
            $('#reader-detail-title').textContent = article.title;
            $('#reader-detail-meta').textContent =
                `${article.categoryName || '未分类'} · ${article.authorName || '匿名作者'} · `
                + `${fmt(article.publishTime)} · 阅读 ${article.viewCount || 0}`;
            $('#reader-detail-body').textContent = article.content || '';
            $('#reader-list-view').hidden = true;
            $('#reader-detail-view').hidden = false;
            window.scrollTo({top: 0, behavior: 'smooth'});
        } catch (error) {
            $('#reader-articles').innerHTML = `<div class="reader-empty">${esc(error.message)}</div>`;
        }
    }

    $('#reader-back').addEventListener('click', () => {
        $('#reader-detail-view').hidden = true;
        $('#reader-list-view').hidden = false;
    });

    $('#reader-search').addEventListener('submit', (event) => {
        event.preventDefault();
        state.title = $('#reader-keyword').value.trim();
        state.pageNum = 1;
        loadArticles();
    });

    loadCategories();
    loadArticles();
})();
