$(document).ready(function() {
    
    var url = new URL(window.location.href);
    var old_vals = url.searchParams.get("submitted");
    
    if (old_vals == null) return;
                  
    $('body').append(`<p style='text-align:center;'>${old_vals}</p>`);
                 
    old_vals = old_vals.replace(/[{ }]/g,'').split(',');
    
    old_vals.forEach(function(elem) {
        elem = elem.split('=');
        target = $(`[name='${elem[0]}']`);
        target.each(function() {
            var el = $(this);
            if (el[0].type != 'checkbox' && el[0].type != 'hidden') el.val(elem[1]);
            else if (el[0].type != 'hidden' && elem[1] == 'True') el.prop('checked', true);
        });
    });
});
