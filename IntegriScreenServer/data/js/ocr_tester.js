$(document).ready(function() {
    
    var url = new URL(window.location.href);
    var testing = url.searchParams.get("ocr_testing");
    var time = url.searchParams.get("time");
    var limit = url.searchParams.get("time");
    
    if (testing == null) return;
    if (time == null) time = 10000;
    if (limit == null) limit = 109
                  
    setTimeout(function() {
        target = parseInt(testing);
        limit = parseInt(limit);
        if (target == limit) window.location.href = window.location.href.replace(/Random_\d.html/, 'stop.html');
        window.location.href = window.location.href.replace(/Random_\d.html/, `Random_${target+1}.html`).replace(/ocr_testing=\d/, `ocr_testing=${target+1}`);
    }, parseInt(time));
                  
});
