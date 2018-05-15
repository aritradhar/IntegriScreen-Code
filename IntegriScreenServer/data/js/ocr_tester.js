$(document).ready(function() {
    
    var url = new URL(window.location.href);
    var setup = url.searchParams.get("setup");
    var time = url.searchParams.get("time");
    var start = url.searchParams.get("start");
    
    if (setup == null) return;
    time = time == null ? time = 10000 : parseInt(time);
    start = start == null ? start = 0 : parseInt(start);
    
    iframe = $('#target')
                  
    $.get(setup, function(data) {
        
        targets = data.split('\n');
        
        tester = setInterval(function() {
            if (start == targets.length) clearInterval(tester);
            var target = start != targets.length ? targets[start] : '__STOP__.html';
            console.log(target);
            
            iframe.attr('src', `http://tildem.inf.ethz.ch/generated/${target}?${url.searchParams.toString()}`);
            start += 1;
        }, time);
    });
                  
});
