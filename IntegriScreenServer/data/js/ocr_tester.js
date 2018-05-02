$(document).ready(function() {
    
    var url = new URL(window.location.href);
    var testing = url.searchParams.get("ocr_testing");
    
    if (testing == null) return;
                  
    setTimeout(function() {
        window.location.href = window.location.href.replace(/Random_\d.html/, `Random_${parseInt(testing)+1}.html`).replace(/ocr_testing=\d/, `ocr_testing=${parseInt(testing)+1}`);}, 10000);
                  
});
