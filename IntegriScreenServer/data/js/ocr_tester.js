$(document).ready(function() {
        
    var url = new URL(window.location.href);
    var setup = url.searchParams.get("setup");
    var time = url.searchParams.get("time");
    var start = url.searchParams.get("start");
    
    if (setup == null) return;
    start = start == null ? start = 0 : parseInt(start);
    
    iframe = $('#target');
    
    function next_page() { 
        var target = start != targets.length ? targets[start] : '__STOP__.html';
        console.log(target);
        
        iframe.attr('src', `http://tildem.inf.ethz.ch/generated/${target}?${url.searchParams.toString()}`);
        start += 1;
    }
    window.next = next_page;
                  
    $.get(setup, function(data) {
        
        targets = data.split('\n');
        next_page();
        
        if (time != null) {
        
            tester = setInterval(function() {
                if (start == targets.length) clearInterval(tester);
                next_page();
            }, parseInt(time));
        }
    });
                  
});
