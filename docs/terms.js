// Chooses which Terms of Service apply by today's date (UTC), so a Terms change needs no work on its effective day.
// Each version lives at terms/<effective date>.html (English) and terms/<effective date>_pl.html (Polish) and is
// listed in terms_versions.json, which the app reads too. To change the Terms: add the two pages, the date, and a
// link in the fixed pages' fallback lists, at least 14 days ahead (the Terms promise that much notice).
//
// - Fixed pages (terms_of_service*.html) carry data-terms-pick="current" or "upcoming" and redirect to that version
//   ("upcoming" falls back to the current one when no change is announced).
// - Version pages fill their #terms-status box: upcoming, current with a change announced, or archived.
// - A version page's #terms-changes box (what changes from the previous version) shows only while that version is
//   upcoming. It is hidden here rather than in the HTML, so readers without JavaScript still see the announcement.
(function () {
    var base = new URL('.', document.currentScript.src);

    function today() {
        return new Date().toISOString().slice(0, 10);
    }

    function pageUrl(version, lang) {
        return new URL('terms/' + version + (lang === 'pl' ? '_pl' : '') + '.html', base).href;
    }

    function formatDate(version, lang) {
        var text = new Date(version + 'T00:00:00Z').toLocaleDateString(lang === 'pl' ? 'pl-PL' : 'en-US',
            { year: 'numeric', month: 'long', day: 'numeric', timeZone: 'UTC' });
        return lang === 'pl' ? text + ' r.' : text;
    }

    function link(href, text) {
        return '<a href="' + href + '">' + text + '</a>';
    }

    /** The version in effect today (the earliest one if none is yet) and the next announced one, or null. */
    function split(versions) {
        var t = today();
        var current = versions.filter(function (v) { return v <= t; }).pop() || versions[0];
        var upcoming = versions.filter(function (v) { return v > t; })[0] || null;
        return { current: current, upcoming: upcoming };
    }

    function statusHtml(version, versions, lang) {
        var s = split(versions);
        var pl = lang === 'pl';
        if (version > today()) {
            return pl
                ? '<strong>Nowa wersja.</strong> Ten Regulamin wchodzi w życie ' + formatDate(version, lang) +
                  ' Do tego dnia obowiązuje ' + link(pageUrl(s.current, lang), 'obecny Regulamin') + '.'
                : '<strong>Upcoming version.</strong> These Terms take effect on ' + formatDate(version, lang) +
                  '. Until then, the ' + link(pageUrl(s.current, lang), 'current Terms') + ' apply.';
        }
        if (version === s.current) {
            if (s.upcoming === null) return '';
            return pl
                ? '<strong>Nadchodząca zmiana:</strong> ' + formatDate(s.upcoming, lang) + ' wchodzi w życie nowy Regulamin. ' +
                  link(pageUrl(s.upcoming, lang), 'Zobacz nowy Regulamin i co się zmienia') + '.'
                : '<strong>Upcoming change:</strong> new Terms take effect on ' + formatDate(s.upcoming, lang) + '. ' +
                  link(pageUrl(s.upcoming, lang), 'Read the new Terms and what changes') + '.';
        }
        var next = versions[versions.indexOf(version) + 1];
        return pl
            ? '<strong>Wersja archiwalna</strong>, zastąpiona nową ' + formatDate(next, lang) + ' ' +
              link(pageUrl(s.current, lang), 'Zobacz obecny Regulamin') + '.'
            : '<strong>Archived version</strong>, replaced on ' + formatDate(next, lang) + '. ' +
              link(pageUrl(s.current, lang), 'Read the current Terms') + '.';
    }

    var changes = document.getElementById('terms-changes');
    if (changes && changes.getAttribute('data-version') <= today()) changes.hidden = true;

    fetch(new URL('terms_versions.json', base), { cache: 'no-cache' })
        .then(function (response) { return response.json(); })
        .then(function (data) {
            var versions = data.versions.slice().sort();
            var pick = document.body.getAttribute('data-terms-pick');
            if (pick) {
                var s = split(versions);
                var target = pick === 'upcoming' && s.upcoming ? s.upcoming : s.current;
                location.replace(pageUrl(target, document.body.getAttribute('data-lang')) + location.hash);
                return;
            }
            var box = document.getElementById('terms-status');
            var version = box && box.getAttribute('data-version');
            if (!box || versions.indexOf(version) < 0) return;
            box.innerHTML = statusHtml(version, versions, box.getAttribute('data-lang'));
        })
        .catch(function () {
            // Fixed pages keep their list of all versions; version pages simply show no status box
        });
})();
