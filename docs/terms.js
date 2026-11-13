// Chooses which version of a legal document applies by today's date (UTC), so a change needs no work on its
// effective day. Used by the Terms of Service, the Privacy Policy and "Above all" (despite the file name).
//
// Each version is <dir>/<effective date>.html (+ _pl.html where there is a Polish one) and is listed in
// terms_versions.json, which the app reads too: "versions" holds the Terms (the name older app releases read),
// "privacy_policy" and "above_all" the others. To change a document: add its page(s), the date, and a link in its
// fixed pages' fallback lists, at least 14 days ahead (the Terms promise that much notice).
//
// - Fixed pages carry data-doc and data-pick="current" or "upcoming" on <body> and redirect to that version
//   ("upcoming" falls back to the current one when no change is announced).
// - Version pages fill their #doc-status box (data-doc, data-version, data-lang): upcoming, current with a change
//   announced, or archived.
// - A version page's #doc-changes box (what changes from the previous version) shows only while that version is
//   upcoming. It is hidden here rather than in the HTML, so readers without JavaScript still see the announcement.
//
// Testing: add ?today=YYYY-MM-DD to any of these pages to see them as on that date. The links and redirects the
// script makes keep the parameter, so you can click through.
(function () {
    var base = new URL('.', document.currentScript.src);

    function link(href, text) {
        return '<a href="' + href + '">' + text + '</a>';
    }

    /** Per document: its folder, its key in terms_versions.json and the status texts ({@code a} makes a link). */
    var DOCS = {
        terms: {
            dir: 'terms/', key: 'versions',
            en: {
                upcoming: function (d, a) { return '<strong>Upcoming version.</strong> These Terms take effect on ' + d + '. Until then, the ' + a('current Terms') + ' apply.'; },
                announced: function (d, a) { return '<strong>Upcoming change:</strong> new Terms take effect on ' + d + '. ' + a('Read the new Terms and what changes') + '.'; },
                archived: function (d, a) { return '<strong>Archived version</strong>, replaced on ' + d + '. ' + a('Read the current Terms') + '.'; }
            },
            pl: {
                upcoming: function (d, a) { return '<strong>Nowa wersja.</strong> Ten Regulamin wchodzi w życie ' + d + ' Do tego dnia obowiązuje ' + a('obecny Regulamin') + '.'; },
                announced: function (d, a) { return '<strong>Nadchodząca zmiana:</strong> ' + d + ' wchodzi w życie nowy Regulamin. ' + a('Zobacz nowy Regulamin i co się zmienia') + '.'; },
                archived: function (d, a) { return '<strong>Wersja archiwalna</strong>, zastąpiona nową ' + d + ' ' + a('Zobacz obecny Regulamin') + '.'; }
            }
        },
        privacy_policy: {
            dir: 'privacy/', key: 'privacy_policy',
            en: {
                upcoming: function (d, a) { return '<strong>Upcoming version.</strong> This Privacy Policy takes effect on ' + d + '. Until then, the ' + a('current Privacy Policy') + ' applies.'; },
                announced: function (d, a) { return '<strong>Upcoming change:</strong> a new Privacy Policy takes effect on ' + d + '. ' + a('Read the new Privacy Policy and what changes') + '.'; },
                archived: function (d, a) { return '<strong>Archived version</strong>, replaced on ' + d + '. ' + a('Read the current Privacy Policy') + '.'; }
            }
        },
        above_all: {
            dir: 'above_all/', key: 'above_all',
            en: {
                upcoming: function (d, a) { return '<strong>Upcoming version.</strong> This version takes effect on ' + d + '. Until then, the ' + a('current version') + ' applies.'; },
                announced: function (d, a) { return '<strong>Upcoming change:</strong> a new version takes effect on ' + d + '. ' + a('Read the new version') + '.'; },
                archived: function (d, a) { return '<strong>Archived version</strong>, replaced on ' + d + '. ' + a('Read the current version') + '.'; }
            },
            pl: {
                upcoming: function (d, a) { return '<strong>Nowa wersja.</strong> Ta wersja wchodzi w życie ' + d + ' Do tego dnia obowiązuje ' + a('obecna wersja') + '.'; },
                announced: function (d, a) { return '<strong>Nadchodząca zmiana:</strong> ' + d + ' wchodzi w życie nowa wersja. ' + a('Zobacz nową wersję') + '.'; },
                archived: function (d, a) { return '<strong>Wersja archiwalna</strong>, zastąpiona nową ' + d + ' ' + a('Zobacz obecną wersję') + '.'; }
            }
        }
    };

    /** Shown when no version of a document is in effect yet, so there is no current one to point to. */
    var FIRST = {
        en: function (d) { return '<strong>Upcoming.</strong> Takes effect on ' + d + '.'; },
        pl: function (d) { return '<strong>Nowy dokument.</strong> Wchodzi w życie ' + d; }
    };

    var todayOverride = new URLSearchParams(location.search).get('today');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(todayOverride || '')) todayOverride = null;

    function today() {
        return todayOverride || new Date().toISOString().slice(0, 10);
    }

    function pageUrl(doc, version, lang) {
        var url = new URL(DOCS[doc].dir + version + (lang === 'pl' ? '_pl' : '') + '.html', base);
        if (todayOverride) url.searchParams.set('today', todayOverride);
        return url.href;
    }

    function formatDate(version, lang) {
        var text = new Date(version + 'T00:00:00Z').toLocaleDateString(lang === 'pl' ? 'pl-PL' : 'en-US',
            { year: 'numeric', month: 'long', day: 'numeric', timeZone: 'UTC' });
        return lang === 'pl' ? text + ' r.' : text;
    }

    /** The version in effect today (the earliest one if none is yet) and the next announced one, or null. */
    function split(versions) {
        var t = today();
        var current = versions.filter(function (v) { return v <= t; }).pop() || versions[0];
        var upcoming = versions.filter(function (v) { return v > t; })[0] || null;
        return { current: current, upcoming: upcoming };
    }

    function statusHtml(doc, version, versions, lang) {
        var s = split(versions);
        var text = DOCS[doc][lang] || DOCS[doc].en;
        function to(target) {
            return function (label) { return link(pageUrl(doc, target, lang), label); };
        }
        if (version > today()) {
            if (s.current > today()) return (FIRST[lang] || FIRST.en)(formatDate(version, lang));
            return text.upcoming(formatDate(version, lang), to(s.current));
        }
        if (version === s.current) {
            return s.upcoming === null ? '' : text.announced(formatDate(s.upcoming, lang), to(s.upcoming));
        }
        var next = versions[versions.indexOf(version) + 1];
        return text.archived(formatDate(next, lang), to(s.current));
    }

    var changes = document.getElementById('doc-changes');
    if (changes && changes.getAttribute('data-version') <= today()) changes.hidden = true;

    var pick = document.body.getAttribute('data-pick');
    var box = document.getElementById('doc-status');
    var doc = pick ? document.body.getAttribute('data-doc') : box && box.getAttribute('data-doc');
    if (!DOCS[doc]) return;

    fetch(new URL('terms_versions.json', base), { cache: 'no-cache' })
        .then(function (response) { return response.json(); })
        .then(function (data) {
            var versions = (data[DOCS[doc].key] || []).slice().sort();
            if (versions.length === 0) return;
            if (pick) {
                var s = split(versions);
                var target = pick === 'upcoming' && s.upcoming ? s.upcoming : s.current;
                location.replace(pageUrl(doc, target, document.body.getAttribute('data-lang')) + location.hash);
                return;
            }
            var version = box.getAttribute('data-version');
            if (versions.indexOf(version) < 0) return;
            box.innerHTML = statusHtml(doc, version, versions, box.getAttribute('data-lang'));
        })
        .catch(function () {
            // Fixed pages keep their list of all versions; version pages simply show no status box
        });
})();
