$(document).ready(function() {
    
    var url = new URL(window.location.href);
    var old_vals = url.searchParams.get("submitted");
    
    if (old_vals == null) return;
                  
    $('body').append(`<pre style='text-align:center;'>${old_vals}</pre>`);
                 
    old_vals = JSON.parse(old_vals);
    
    for (var key in old_vals) {
        target = $(`[name='${key}']`);
        target.each(function() {
            var el = $(this);
            if (el[0].type != 'checkbox' && el[0].type != 'hidden') el.val(old_vals[key]);
            else if (el[0].type != 'hidden' && old_vals[key] == 'True') el.prop('checked', true);
        });
    }
});
