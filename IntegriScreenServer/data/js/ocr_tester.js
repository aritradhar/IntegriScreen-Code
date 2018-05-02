$(document).ready(function() {
    
    var url = new URL(window.location.href);
    var testing = url.searchParams.get("ocr_testing");
    var time = url.searchParams.get("time");
    var limit = url.searchParams.get("limit");
    
    if (testing == null) return;
    if (time == null) time = 10000;
    if (limit == null) limit = 110;
                  
    setTimeout(function() {
        target = parseInt(testing);
        limit = parseInt(limit);
        next_target = target + 1;
        console.log(target); console.log(limit); console.log(next_target);
        if (next_target == limit) {
            window.location.href = window.location.href.replace(/Random_\d+\.html/, '__STOP__.html');
            return; // makes no sense but maybe no way it makes 100% sense otherwise it goes on executing
        }
        next_destination = window.location.href.replace(/Random_\d+\.html/, `Random_${next_target}.html`);
        next_destination = next_destination.replace(/ocr_testing=\d+/, `ocr_testing=${next_target}`);
        window.location.href = next_destination;
    }, parseInt(time));
                  
});
