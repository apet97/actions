(function () {
    'use strict';

    var token = null;
    var pollErrors = 0;
    var pollHandle = null;

    function init() {
        token = extractToken();
        if (!token) {
            showExpiredState();
            return;
        }
        schedulePoll(0);
    }

    function extractToken() {
        var params = new URLSearchParams(window.location.search);
        var nextToken = params.get('auth_token');
        if (nextToken) {
            params.delete('auth_token');
            var nextUrl = window.location.pathname + (params.toString() ? '?' + params.toString() : '');
            window.history.replaceState({}, document.title, nextUrl);
        }
        return nextToken;
    }

    function schedulePoll(delay) {
        clearTimeout(pollHandle);
        pollHandle = window.setTimeout(loadStats, delay);
    }

    function loadStats() {
        fetch('/api/widget/stats', {
            headers: {
                'X-Addon-Token': token,
                'X-Requested-With': 'fetch'
            }
        })
            .then(function (response) {
                if (response.status === 401) {
                    throw new Error('SESSION_EXPIRED');
                }
                if (!response.ok) {
                    throw new Error('HTTP_' + response.status);
                }
                return response.json();
            })
            .then(function (status) {
                pollErrors = 0;
                updateStats(status);
                schedulePoll(60000);
            })
            .catch(function (error) {
                if (error && error.message === 'SESSION_EXPIRED') {
                    showExpiredState();
                    return;
                }
                pollErrors = Math.min(pollErrors + 1, 5);
                schedulePoll(Math.min(60000 * Math.pow(2, pollErrors - 1), 300000));
            });
    }

    function updateStats(status) {
        if (!status || !status.stats) {
            return;
        }

        document.getElementById('widget-active-actions').textContent = String(status.stats.activeActionCount);
        document.getElementById('widget-total-executions').textContent = String(status.stats.totalExecutions24h);
        document.getElementById('widget-failed-executions').textContent = String(status.stats.failedExecutions24h);

        var badge = document.getElementById('widget-success-rate');
        badge.className = 'ha-badge ha-badge--' + status.rateClass;
        badge.textContent = status.rateBadgePrefix + ' ' + Number(status.stats.successRate24h).toFixed(1) + '%';
        badge.setAttribute('aria-label', status.rateBadgePrefix + ' success rate');
    }

    function showExpiredState() {
        document.body.classList.add('widget-session-expired');
        var message = document.body.getAttribute('data-session-expired-message');
        var expired = document.getElementById('widget-expired');
        if (message && expired) {
            expired.textContent = message;
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
