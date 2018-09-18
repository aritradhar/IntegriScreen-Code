$(document).ready(function() {

	var serverUrl = "https://punk.cs.ox.ac.uk/IntegriScreenServer/"
	// var serverUrl = "http://idvm-infk-capkun01.inf.ethz.ch:8085"
	// var serverUrl = "http://tildem.inf.ethz.ch"

    var url = new URL(window.location.href);
    var fileList = url.searchParams.get("list");
    var time = url.searchParams.get("time");
    var start = url.searchParams.get("start");

    if (fileList == null) return;
    start = start == null ? start = 0 : parseInt(start);

    iframe = $('#target');

    function next_page() {
        var target = start != page_targets.length -1 ? page_targets[start] : '__STOP__.html';
        console.log(target);

        iframe.attr('src', serverUrl + `/generated/${target}?${url.searchParams.toString()}`);
        start += 1;
    }
    window.next = next_page;

    $.get("../data/"+fileList, function(data) {

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
