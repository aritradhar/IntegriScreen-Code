$(document).ready(function() {

    var url = new URL(window.location.href);
    var setup = url.searchParams.get("setup");
    var time = url.searchParams.get("time");
    var start = url.searchParams.get("start");

    if (setup == null) return;
    start = start == null ? start = 0 : parseInt(start);

    iframe = $('#target');

    function next_page() {
        var target = start != page_targets.length -1 ? page_targets[start] : '__STOP__.html';
        console.log(target);

        iframe.attr('src', `http://tildem.inf.ethz.ch/generated/${target}?${url.searchParams.toString()}`);
        start += 1;
    }
    window.next = next_page;

    $.get(setup, function(data) {

        page_targets = data.split('\n');
        next_page();

        if (time != null) {

            tester = setInterval(function() {
                if (start == page_targets.length-1) clearInterval(tester);
                next_page();
            }, parseInt(time));
        }
    });

});
