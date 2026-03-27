/**
 * HTTP Actions Sidebar — Vanilla JS
 * Handles action CRUD, test-fire, execution logs, token refresh,
 * templates, import/export, conditions, and variable hints.
 * All DOM manipulation uses safe methods (createElement/textContent) — no innerHTML.
 */
(function () {
    'use strict';

    // ── State ──────────────────────────────────────────────────
    var token = null;
    var currentActionId = null;
    var actions = [];
    var eventSchemas = {};
    // M22: derive parent origin from referrer, accepting all Clockify regional subdomains
    var parentOrigin = (function() {
        try {
            var ref = document.referrer;
            if (ref) {
                var url = new URL(ref);
                if (url.hostname.endsWith('.clockify.me')) {
                    return url.origin;
                }
            }
        } catch (e) {}
        return 'https://app.clockify.me';
    })();
    var appConfig = window.APP_CONFIG || {};
    var uiLanguage = appConfig.language || document.body.getAttribute('data-language') || navigator.language || 'en';
    var uiTimezone = appConfig.timezone || document.body.getAttribute('data-timezone') || Intl.DateTimeFormat().resolvedOptions().timeZone;
    var messages = appConfig.messages || {};

    function msg(key, fallback) {
        return Object.prototype.hasOwnProperty.call(messages, key) ? messages[key] : fallback;
    }

    function formatMessage(template, args) {
        return template.replace(/\{(\d+)}/g, function (_, index) {
            return args[index] !== undefined ? args[index] : _;
        });
    }

    function formatApiError(err, fallback) {
        if (!err) return fallback;
        return err.message || err.error || fallback;
    }

    function formatTime(value) {
        try {
            return new Intl.DateTimeFormat(uiLanguage, {
                hour: 'numeric',
                minute: '2-digit',
                second: '2-digit',
                timeZone: uiTimezone
            }).format(new Date(value));
        } catch (e) {
            return new Date(value).toLocaleTimeString();
        }
    }

    // ── Init ───────────────────────────────────────────────────
    function init() {
        extractToken();
        setupEventListeners();
        setupTokenRefresh();
        loadActions();
        loadLogs();
        loadEventSchemas();
    }

    // L18+L19: removed unused claims/workspaceId variables
    function extractToken() {
        var params = new URLSearchParams(window.location.search);
        token = params.get('auth_token');
        if (token) {
            params.delete('auth_token');
            var next = location.pathname + (params.toString() ? '?' + params : '');
            history.replaceState({}, document.title, next);
        }
    }

    function getHeaders() {
        return { 'X-Addon-Token': token, 'Content-Type': 'application/json' };
    }

    // ── Token Refresh (every 25 min) ──────────────────────────
    // M21: unified token refresh message format (Clockify expects `title` field)
    function setupTokenRefresh() {
        setInterval(function () {
            if (window.parent) {
                window.parent.postMessage({ title: 'refreshAddonToken' }, parentOrigin);
            }
        }, 25 * 60 * 1000);

        window.addEventListener('message', function (event) {
            if (event.origin !== parentOrigin) return;
            if (event.data && typeof event.data === 'object') {
                if (event.data.title === 'refreshAddonToken' && event.data.body) {
                    token = event.data.body;
                }
            }
        });
    }

    // ── Event Listeners ───────────────────────────────────────
    function setupEventListeners() {
        document.getElementById('btn-new-action').addEventListener('click', showNewActionForm);
        document.getElementById('btn-close-form').addEventListener('click', hideForm);
        document.getElementById('btn-add-header').addEventListener('click', function () { addHeaderRow('', ''); });
        document.getElementById('btn-save').addEventListener('click', saveAction);
        document.getElementById('btn-delete').addEventListener('click', deleteAction);
        document.getElementById('btn-test').addEventListener('click', testAction);
        document.getElementById('btn-refresh-logs').addEventListener('click', loadLogs);
        document.getElementById('btn-close-modal').addEventListener('click', closeTestModal);
        document.getElementById('btn-close-modal-footer').addEventListener('click', closeTestModal);

        // P1/P2 listeners
        document.getElementById('btn-add-condition').addEventListener('click', function () { addConditionRow('conditions-container', '', 'equals', ''); });
        document.getElementById('btn-add-success-condition').addEventListener('click', function () { addSuccessConditionRow('', 'status_range', ''); });
        document.getElementById('btn-from-template').addEventListener('click', showTemplateModal);
        document.getElementById('btn-close-template-modal').addEventListener('click', closeTemplateModal);
        document.getElementById('btn-import-export').addEventListener('click', showImportExportModal);
        document.getElementById('btn-close-ie-modal').addEventListener('click', closeImportExportModal);
        document.getElementById('btn-export').addEventListener('click', exportActions);
        document.getElementById('btn-import').addEventListener('click', importActions);
        document.getElementById('input-event-type').addEventListener('change', onEventTypeChange);
    }

    // ── DOM Helpers ────────────────────────────────────────────
    function el(tag, className, textContent) {
        var node = document.createElement(tag);
        if (className) node.className = className;
        if (textContent !== undefined) node.textContent = textContent;
        return node;
    }

    function clearChildren(parent) {
        while (parent.firstChild) parent.removeChild(parent.firstChild);
    }

    // ── API Calls ─────────────────────────────────────────────
    function api(method, path, body) {
        var opts = { method: method, headers: getHeaders() };
        if (body !== undefined) opts.body = JSON.stringify(body);
        return fetch(path, opts).then(function (r) {
            if (r.status === 401) {
                showToast('error', msg('sessionExpired', 'Session expired. Please reload.'));
                return null;
            }
            if (!r.ok) {
                var contentType = r.headers.get('content-type') || '';
                if (contentType.indexOf('application/json') !== -1) {
                    return r.json().then(function (err) { throw err; });
                }
                return r.text().then(function (text) {
                    throw { error: text || r.statusText || msg('unknownError', 'Unknown error') };
                });
            }
            if (r.status === 204 || method === 'DELETE') return null;
            if ((r.headers.get('content-type') || '').indexOf('application/json') !== -1) {
                return r.json();
            }
            return null;
        });
    }

    // ── Event Schemas (F15) ───────────────────────────────────
    function loadEventSchemas() {
        api('GET', '/api/events').then(function (data) {
            if (!data) return;
            data.forEach(function (evt) {
                eventSchemas[evt.name] = evt.variables || [];
            });
        }).catch(function () { /* non-critical */ });
    }

    function onEventTypeChange() {
        var eventType = document.getElementById('input-event-type').value;
        var hintsDiv = document.getElementById('variable-hints');
        var listDiv = document.getElementById('variable-list');
        clearChildren(listDiv);

        var vars = eventSchemas[eventType];
        if (!vars || vars.length === 0) {
            hintsDiv.style.display = 'none';
            return;
        }

        hintsDiv.style.display = 'block';
        vars.forEach(function (v) {
            var chip = el('span', 'ha-variable-chip');
            chip.textContent = '{{' + v.path + '}}';
            chip.title = (v.description || '') + ' (' + (v.type || 'string') + ')';
            chip.addEventListener('click', function () {
                var bodyEl = document.getElementById('input-body');
                var pos = bodyEl.selectionStart || bodyEl.value.length;
                var text = '{{' + v.path + '}}';
                bodyEl.value = bodyEl.value.substring(0, pos) + text + bodyEl.value.substring(pos);
                bodyEl.focus();
                bodyEl.setSelectionRange(pos + text.length, pos + text.length);
            });
            listDiv.appendChild(chip);
        });
    }

    // ── Actions ───────────────────────────────────────────────
    function loadActions() {
        document.body.classList.add('ha-loading');
        api('GET', '/api/actions').then(function (data) {
            if (!data) return;
            actions = data;
            renderActionList();
        }).catch(function (err) { console.error('Failed to load actions:', err); })
        .finally(function () { document.body.classList.remove('ha-loading'); });
    }

    function renderActionList() {
        var list = document.getElementById('action-list');
        clearChildren(list);

        if (actions.length === 0) {
            var empty = el('div', 'ha-empty-state clockify-text clockify-text-caption text-muted',
                msg('emptyActions', 'No actions yet. Click "+ New Action" to get started.'));
            list.appendChild(empty);
            return;
        }

        actions.forEach(function (action) {
            var card = el('div', 'ha-action-card');
            card.setAttribute('data-id', action.id);
            // A5: keyboard accessibility
            card.setAttribute('tabindex', '0');
            card.setAttribute('role', 'button');
            card.setAttribute('aria-label', formatMessage(
                msg('editActionAria', 'Edit action: {0}'),
                [action.name]
            ));

            var info = el('div', 'ha-action-card-info');
            info.appendChild(el('span', 'clockify-text clockify-text-body2', action.name));
            info.appendChild(el('span', 'mp-tag info', action.eventType.replace(/_/g, ' ')));

            var meta = el('div', 'ha-action-card-meta');
            var methodClass = action.httpMethod === 'DELETE' ? 'mp-tag danger' : 'mp-tag info';
            meta.appendChild(el('span', methodClass, action.httpMethod));
            var statusClass = action.enabled ? 'status approved' : 'status draft';
            meta.appendChild(el('span', statusClass, action.enabled
                ? msg('statusActive', 'Active')
                : msg('statusDisabled', 'Disabled')));
            if (action.chainOrder) {
                meta.appendChild(el('span', 'mp-tag', '#' + action.chainOrder));
            }

            card.appendChild(info);
            card.appendChild(meta);

            card.addEventListener('click', function () { editAction(action.id); });
            card.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); editAction(action.id); }
            });
            list.appendChild(card);
        });
    }

    function showNewActionForm() {
        currentActionId = null;
        document.getElementById('form-title').textContent = msg('formNewAction', 'New Action');
        document.getElementById('input-name').value = '';
        document.getElementById('input-event-type').value = '';
        document.getElementById('input-method').value = 'POST';
        document.getElementById('input-url').value = '';
        document.getElementById('input-body').value = '';
        document.getElementById('input-retry').value = '3';
        document.getElementById('input-enabled').checked = true;
        document.getElementById('input-chain-order').value = '';
        document.getElementById('input-cron').value = '';
        document.getElementById('input-signing-secret').value = '';
        clearChildren(document.getElementById('headers-container'));
        clearChildren(document.getElementById('conditions-container'));
        clearChildren(document.getElementById('success-conditions-container'));
        document.getElementById('btn-delete').style.display = 'none';
        document.getElementById('variable-hints').style.display = 'none';
        document.getElementById('action-form').style.display = 'block';
    }

    function editAction(id) {
        var action = actions.find(function (a) { return a.id === id; });
        if (!action) return;

        currentActionId = id;
        document.getElementById('form-title').textContent = msg('formEditAction', 'Edit Action');
        document.getElementById('input-name').value = action.name;
        document.getElementById('input-event-type').value = action.eventType;
        document.getElementById('input-method').value = action.httpMethod;
        document.getElementById('input-url').value = action.urlTemplate;
        document.getElementById('input-body').value = action.bodyTemplate || '';
        document.getElementById('input-retry').value = action.retryCount;
        document.getElementById('input-enabled').checked = action.enabled;
        document.getElementById('input-chain-order').value = action.chainOrder || '';
        document.getElementById('input-cron').value = action.cronExpression || '';
        document.getElementById('input-signing-secret').value = '';
        document.getElementById('btn-delete').style.display = 'inline-block';

        var container = document.getElementById('headers-container');
        clearChildren(container);
        if (action.headers) {
            Object.entries(action.headers).forEach(function (entry) {
                addHeaderRow(entry[0], entry[1]);
            });
        }

        // Execution conditions
        var condContainer = document.getElementById('conditions-container');
        clearChildren(condContainer);
        if (action.executionConditions) {
            action.executionConditions.forEach(function (c) {
                addConditionRow('conditions-container', c.field, c.operator, c.value);
            });
        }

        // Success conditions
        var scContainer = document.getElementById('success-conditions-container');
        clearChildren(scContainer);
        if (action.successConditions) {
            action.successConditions.forEach(function (c) {
                addSuccessConditionRow('', c.operator, c.value);
            });
        }

        onEventTypeChange();
        document.getElementById('action-form').style.display = 'block';
    }

    function hideForm() {
        document.getElementById('action-form').style.display = 'none';
        currentActionId = null;
    }

    // L20: debounce save to prevent duplicate creation from rapid clicks
    var saving = false;
    function saveAction() {
        if (saving) return;
        var payload = buildPayload();
        if (!payload) return;
        saving = true;
        document.body.classList.add('ha-loading');
        var method = currentActionId ? 'PUT' : 'POST';
        var url = currentActionId ? '/api/actions/' + currentActionId : '/api/actions';

        api(method, url, payload).then(function (data) {
            saving = false;
            if (data) {
                showToast('success', currentActionId
                    ? msg('actionUpdated', 'Action updated')
                    : msg('actionCreated', 'Action created'));
                hideForm();
                loadActions();
            }
        }).catch(function (err) {
            saving = false;
            showToast('error', formatApiError(err, msg('saveFailed', 'Failed to save action')));
        }).finally(function () { document.body.classList.remove('ha-loading'); });
    }

    function deleteAction() {
        if (!currentActionId) return;
        if (!confirm(msg('deleteConfirm', 'Delete this action? This cannot be undone.'))) return;

        document.body.classList.add('ha-loading');
        api('DELETE', '/api/actions/' + currentActionId).then(function () {
            showToast('success', msg('actionDeleted', 'Action deleted'));
            hideForm();
            loadActions();
            loadLogs();
        }).catch(function (err) {
            showToast('error', formatApiError(err, msg('deleteFailed', 'Failed to delete action')));
        }).finally(function () { document.body.classList.remove('ha-loading'); });
    }

    var testing = false;
    function testAction() {
        if (testing) return;
        if (!currentActionId) {
            showToast('warning', msg('saveBeforeTest', 'Save the action first before testing'));
            return;
        }
        testing = true;
        document.body.classList.add('ha-loading');
        showToast('info', msg('sendingTest', 'Sending test request...'));
        api('POST', '/api/actions/' + currentActionId + '/test', {}).then(function (result) {
            testing = false;
            if (result) showTestResult(result);
            loadLogs();
        }).catch(function (err) {
            testing = false;
            showToast('error', msg('testFailed', 'Test failed') + ': ' +
                formatApiError(err, msg('unknownError', 'Unknown error')));
        }).finally(function () { document.body.classList.remove('ha-loading'); });
    }

    // ── Headers ───────────────────────────────────────────────
    function addHeaderRow(key, value) {
        var container = document.getElementById('headers-container');
        var row = el('div', 'ha-header-row');

        var keyInput = el('input', 'clockify-input ha-header-key');
        keyInput.placeholder = msg('headerName', 'Header name');
        keyInput.value = key || '';

        var valInput = el('input', 'clockify-input ha-header-value');
        valInput.placeholder = msg('headerValue', 'Value');
        valInput.value = value || '';

        var removeBtn = el('button', 'link sm ha-remove-header', '\u00d7');
        removeBtn.setAttribute('aria-label', 'Remove');
        removeBtn.addEventListener('click', function () { row.remove(); });

        row.appendChild(keyInput);
        row.appendChild(valInput);
        row.appendChild(removeBtn);
        container.appendChild(row);
    }

    function collectHeaders() {
        var hdrs = {};
        document.querySelectorAll('#headers-container .ha-header-row').forEach(function (row) {
            var key = row.querySelector('.ha-header-key').value.trim();
            var value = row.querySelector('.ha-header-value').value;
            if (key) hdrs[key] = value;
        });
        return Object.keys(hdrs).length > 0 ? hdrs : null;
    }

    // ── Condition Rows (F18) ─────────────────────────────────
    function addConditionRow(containerId, field, operator, value) {
        var container = document.getElementById(containerId);
        var row = el('div', 'ha-condition-row');

        var fieldInput = el('input', 'clockify-input ha-cond-field');
        fieldInput.placeholder = msg('conditionFieldPlaceholder', 'event.field.path');
        fieldInput.value = field || '';

        var opSelect = el('select', 'clockify-input ha-cond-op');
        ['equals', 'not_equals', 'contains', 'gt', 'lt', 'exists', 'not_exists'].forEach(function (op) {
            var opt = el('option');
            opt.value = op;
            opt.textContent = op.replace(/_/g, ' ');
            if (op === operator) opt.selected = true;
            opSelect.appendChild(opt);
        });

        var valInput = el('input', 'clockify-input ha-cond-value');
        valInput.placeholder = msg('conditionValuePlaceholder', 'Value');
        valInput.value = value || '';

        var removeBtn = el('button', 'link sm ha-remove-header', '\u00d7');
        removeBtn.setAttribute('aria-label', 'Remove');
        removeBtn.addEventListener('click', function () { row.remove(); });

        row.appendChild(fieldInput);
        row.appendChild(opSelect);
        row.appendChild(valInput);
        row.appendChild(removeBtn);
        container.appendChild(row);
    }

    function collectConditions(containerId) {
        var conds = [];
        document.querySelectorAll('#' + containerId + ' .ha-condition-row').forEach(function (row) {
            var field = row.querySelector('.ha-cond-field').value.trim();
            var operator = row.querySelector('.ha-cond-op').value;
            var value = row.querySelector('.ha-cond-value').value;
            if (field) conds.push({ field: field, operator: operator, value: value });
        });
        return conds.length > 0 ? conds : null;
    }

    // ── Success Condition Rows (F13) ─────────────────────────
    function addSuccessConditionRow(field, operator, value) {
        var container = document.getElementById('success-conditions-container');
        var row = el('div', 'ha-condition-row');

        var opSelect = el('select', 'clockify-input ha-cond-op');
        ['status_range', 'body_contains', 'body_not_contains'].forEach(function (op) {
            var opt = el('option');
            opt.value = op;
            opt.textContent = op.replace(/_/g, ' ');
            if (op === operator) opt.selected = true;
            opSelect.appendChild(opt);
        });

        var valInput = el('input', 'clockify-input ha-cond-value');
        valInput.placeholder = msg('successConditionPlaceholder', 'e.g. 200-299 or "success"');
        valInput.value = value || '';
        valInput.style.flex = '1';

        var removeBtn = el('button', 'link sm ha-remove-header', '\u00d7');
        removeBtn.setAttribute('aria-label', 'Remove');
        removeBtn.addEventListener('click', function () { row.remove(); });

        row.appendChild(opSelect);
        row.appendChild(valInput);
        row.appendChild(removeBtn);
        container.appendChild(row);
    }

    function collectSuccessConditions() {
        var conds = [];
        document.querySelectorAll('#success-conditions-container .ha-condition-row').forEach(function (row) {
            var operator = row.querySelector('.ha-cond-op').value;
            var value = row.querySelector('.ha-cond-value').value;
            if (value) conds.push({ field: '', operator: operator, value: value });
        });
        return conds.length > 0 ? conds : null;
    }

    // ── Payload Builder ───────────────────────────────────────
    function buildPayload() {
        var name = document.getElementById('input-name').value.trim();
        var eventType = document.getElementById('input-event-type').value;
        var method = document.getElementById('input-method').value;
        var url = document.getElementById('input-url').value.trim();
        var body = document.getElementById('input-body').value;
        var retry = parseInt(document.getElementById('input-retry').value) || 3;
        var enabled = document.getElementById('input-enabled').checked;
        var chainOrder = document.getElementById('input-chain-order').value;
        var cronExpr = document.getElementById('input-cron').value.trim();

        if (!name) { showToast('warning', msg('nameRequired', 'Name is required')); return null; }
        if (!eventType) { showToast('warning', msg('selectTrigger', 'Select a trigger event')); return null; }
        if (!url) { showToast('warning', msg('urlRequired', 'URL is required')); return null; }
        // L22: basic URL scheme validation
        if (!/^https?:\/\//i.test(url.replace(/\{\{.*?\}\}/g, ''))) {
            showToast('warning', msg('urlMustStart', 'URL must start with http:// or https://'));
            return null;
        }

        var signingSecret = document.getElementById('input-signing-secret').value.trim();

        var payload = {
            name: name, eventType: eventType, httpMethod: method, urlTemplate: url,
            headers: collectHeaders(), bodyTemplate: body || null, retryCount: retry, enabled: enabled,
            executionConditions: collectConditions('conditions-container'),
            successConditions: collectSuccessConditions(),
            chainOrder: chainOrder ? parseInt(chainOrder) : null,
            cronExpression: cronExpr || null,
            signingSecret: signingSecret || null
        };
        return payload;
    }

    // ── Templates (F20) ──────────────────────────────────────
    function showTemplateModal() {
        var modal = document.getElementById('template-modal');
        var list = document.getElementById('template-list');
        clearChildren(list);

        document.body.classList.add('ha-loading');
        api('GET', '/api/templates').then(function (templates) {
            if (!templates || templates.length === 0) {
                list.appendChild(el(
                    'div',
                    'ha-empty-state clockify-text clockify-text-caption',
                    msg('noTemplatesAvailable', 'No templates available.')
                ));
                return;
            }
            templates.forEach(function (tpl) {
                var card = el('div', 'ha-action-card');
                card.setAttribute('tabindex', '0');
                card.setAttribute('role', 'button');
                var info = el('div', 'ha-action-card-info');
                info.appendChild(el('span', 'clockify-text clockify-text-body2', tpl.name));
                info.appendChild(el('span', 'clockify-text clockify-text-caption text-muted', tpl.description));
                card.appendChild(info);
                card.addEventListener('click', function () {
                    applyTemplate(tpl);
                    closeTemplateModal();
                });
                card.addEventListener('keydown', function (e) {
                    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); applyTemplate(tpl); closeTemplateModal(); }
                });
                list.appendChild(card);
            });
        }).catch(function () {
            list.appendChild(el(
                'div',
                'ha-empty-state clockify-text clockify-text-caption',
                msg('failedToLoadTemplates', 'Failed to load templates.')
            ));
        }).finally(function () { document.body.classList.remove('ha-loading'); });

        modal.style.display = 'flex';
    }

    function applyTemplate(tpl) {
        showNewActionForm();
        document.getElementById('input-name').value = tpl.name;
        document.getElementById('input-event-type').value = tpl.eventType || 'NEW_TIME_ENTRY';
        document.getElementById('input-method').value = tpl.httpMethod || 'POST';
        document.getElementById('input-url').value = tpl.urlTemplate || '';
        document.getElementById('input-body').value = tpl.bodyTemplate || '';
        if (tpl.sampleHeaders) {
            Object.entries(tpl.sampleHeaders).forEach(function (entry) {
                addHeaderRow(entry[0], entry[1]);
            });
        }
        onEventTypeChange();
    }

    function closeTemplateModal() {
        document.getElementById('template-modal').style.display = 'none';
    }

    // ── Import/Export (F16) ───────────────────────────────────
    function showImportExportModal() {
        document.getElementById('import-json').value = '';
        document.getElementById('import-export-modal').style.display = 'flex';
    }

    function closeImportExportModal() {
        document.getElementById('import-export-modal').style.display = 'none';
    }

    function exportActions() {
        document.body.classList.add('ha-loading');
        api('GET', '/api/actions/export').then(function (data) {
            if (!data) return;
            var json = JSON.stringify(data, null, 2);
            document.getElementById('import-json').value = json;
            showToast('success', formatMessage(
                msg('exportedTemplate', 'Exported {0} action(s). Copy the JSON below.'),
                [data.length]
            ));
        }).catch(function (err) {
            showToast('error', msg('exportFailed', 'Export failed') + ': ' +
                formatApiError(err, msg('unknownError', 'Unknown error')));
        }).finally(function () { document.body.classList.remove('ha-loading'); });
    }

    function importActions() {
        var json = document.getElementById('import-json').value.trim();
        if (!json) {
            showToast('warning', msg('importPasteFirst', 'Paste JSON data first'));
            return;
        }
        var parsed;
        try {
            parsed = JSON.parse(json);
        } catch (e) {
            showToast('error', msg('invalidJson', 'Invalid JSON'));
            return;
        }
        if (!Array.isArray(parsed)) {
            showToast('error', msg('jsonMustBeArray', 'JSON must be an array of actions'));
            return;
        }

        document.body.classList.add('ha-loading');
        api('POST', '/api/actions/import', parsed).then(function (data) {
            if (data) {
                showToast('success', formatMessage(
                    msg('importedTemplate', 'Imported {0} action(s), skipped {1}'),
                    [data.created.length, data.skipped.length]
                ));
                closeImportExportModal();
                loadActions();
            }
        }).catch(function (err) {
            showToast('error', msg('importFailed', 'Import failed') + ': ' +
                formatApiError(err, msg('unknownError', 'Unknown error')));
        }).finally(function () { document.body.classList.remove('ha-loading'); });
    }

    // ── Execution Logs ────────────────────────────────────────
    function loadLogs() {
        document.body.classList.add('ha-loading');
        api('GET', '/api/logs?size=20').then(function (logsPage) {
            if (!logsPage || !logsPage.content || logsPage.content.length === 0) {
                document.getElementById('logs-empty').style.display = 'block';
                document.getElementById('logs-table').style.display = 'none';
                return;
            }
            document.getElementById('logs-empty').style.display = 'none';
            document.getElementById('logs-table').style.display = 'table';
            renderLogs(logsPage.content);
        }).catch(function (err) { console.error('Failed to load logs:', err); })
        .finally(function () { document.body.classList.remove('ha-loading'); });
    }

    function renderLogs(logs) {
        var tbody = document.getElementById('logs-tbody');
        clearChildren(tbody);

        logs.forEach(function (log) {
            var tr = el('tr');
            if (log.success) tr.className = 'ha-status-success';
            else if (log.responseStatus && log.responseStatus >= 400 && log.responseStatus < 500) tr.className = 'ha-status-client-error';
            else tr.className = 'ha-status-error';

            tr.appendChild(el('td', null, formatTime(log.executedAt)));

            var eventTd = el('td', 'ellipsis', (log.eventType || '').replace(/_/g, ' '));
            tr.appendChild(eventTd);

            var statusTd = el('td');
            var statusSpan = el('span', 'status ' + (log.success ? 'approved' : 'rejected'),
                String(log.responseStatus || msg('statusErr', 'ERR')));
            statusTd.appendChild(statusSpan);
            tr.appendChild(statusTd);

            tr.appendChild(el('td', null, log.responseTimeMs ? log.responseTimeMs + 'ms' : '-'));
            tbody.appendChild(tr);
        });
    }

    // ── Test Result Modal ─────────────────────────────────────
    function showTestResult(result) {
        var content = document.getElementById('test-result-content');
        clearChildren(content);

        var alertClass = result.success ? 'alert alert-success' : 'alert alert-danger';
        var alertText = (result.success ? msg('testSuccess', 'Success') : msg('testFailure', 'Failed')) +
            (result.responseStatus ? ' \u2014 HTTP ' + result.responseStatus : '') +
            (result.responseTimeMs ? ' (' + result.responseTimeMs + 'ms)' : '');
        content.appendChild(el('div', alertClass, alertText));

        if (result.errorMessage) {
            content.appendChild(el('div', 'alert alert-warning', result.errorMessage));
        }

        if (result.responseBody) {
            var field = el('div', 'ha-field');
            field.appendChild(el('label', 'clockify-text clockify-text-label', msg('responseBody', 'Response Body')));
            field.appendChild(el('pre', 'ha-response-body', result.responseBody));
            content.appendChild(field);
        }
        if (result.responseHeaders && Object.keys(result.responseHeaders).length > 0) {
            var headerField = el('div', 'ha-field');
            headerField.appendChild(el('label', 'clockify-text clockify-text-label', msg('responseHeaders', 'Response Headers')));
            headerField.appendChild(el(
                'pre',
                'ha-response-body',
                Object.keys(result.responseHeaders).sort().map(function (key) {
                    return key + ': ' + result.responseHeaders[key];
                }).join('\n')
            ));
            content.appendChild(headerField);
        }

        document.getElementById('test-modal').style.display = 'flex';
        document.getElementById('test-modal').classList.add('show');
    }

    function closeTestModal() {
        document.getElementById('test-modal').style.display = 'none';
        document.getElementById('test-modal').classList.remove('show');
    }

    // ── Toast (via Clockify window event) ─────────────────────
    function showToast(type, message) {
        try {
            if (window.parent) {
                window.parent.postMessage(JSON.stringify({
                    action: 'toastrPop', payload: { type: type, message: message }
                }), parentOrigin);
            }
        } catch (e) {
            console.log('[' + type.toUpperCase() + '] ' + message);
        }
    }

    // ── A2+A3: Escape key closes modals + basic focus trapping ──
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            var modals = ['test-modal', 'template-modal', 'import-export-modal'];
            modals.forEach(function (id) {
                var modal = document.getElementById(id);
                if (modal && modal.style.display !== 'none') {
                    modal.style.display = 'none';
                }
            });
            var form = document.getElementById('action-form');
            if (form && form.style.display !== 'none') {
                hideForm();
            }
        }
    });

    // ── Boot ──────────────────────────────────────────────────
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
