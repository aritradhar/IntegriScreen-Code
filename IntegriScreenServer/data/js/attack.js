function log(msg) {
    // can be changed if we need to log stuff differently when testing
    console.log(msg);
}

key_count = 0;
function parallel(attack) {
    targets = _.sample($("textarea,input[type='textfield'],input[type='text']"), 2);
    log(`binded-${$(targets[0]).attr('name')}-${$(targets[0]).val()}`);
    $(targets[0]).keydown(function() {
        key_count += 1;
        if (key_count == 3) {
            log(`target-${$(targets[1]).attr('name')}-${$(targets[1]).val()}`);
            attack($(targets[1]));    // chosen by fair dice roll, guaranteed to be random
        }
    });
}
            
timer = null; attacked = 0;
function inactive(attack) {
    target =  _.sample($("textarea,input[type='textfield'],input[type='text']"));
    $(document).keypress(function() {
        if (timer != null) clearTimeout(timer);
        if (!attacked) timer = setTimeout(function() { 
            attacked = 1; 
            log(`target-${$(target).attr('name')}-${$(target).val()}`);
            return attack($(target));
        }, 3000); // different dice roll trust me
    });
}

attack_modes = {
    'inactive': inactive,
    'parallel': parallel
}

alphabet = [...'abcdefghijklmnopqrstuvwxyz'];

function replace_char(target) {
    value = [...target.val()];
    t_char = Math.floor(Math.random() * value.length);
    s_char = value[t_char];
    value[t_char] = _.sample(alphabet);
    target.val(value.join(''));
    // attack - original char - position - new char
    log(`replace_char-${s_char}-${t_char}-${value[t_char]}`);
}

function flip_chars(target) {
    value = [...target.val()];
    t_char = Math.floor(Math.random() * value.length - 1);
    tmp = value[t_char];
    value[t_char] = value[t_char + 1];
    value[t_char + 1] = tmp;
    target.val(value.join(''));
    // attack - position of first - original char 1 - original char 2
    log(`flip_chars-${t_char}-${value[t_char+1]}-${value[t_char]}`);
}

function add_char(target) {
    value = [...target.val()];
    t_char = Math.floor(Math.random() * value.length);
    n_char = _.sample(alphabet);
    value.splice(t_char, 0, n_char);
    target.val(value.join(''));
    // attack - new char - position
    log(`add_char-${n_char}-${t_char}`);
}

function remove_char(target) {
    value = [...target.val()];
    t_char = Math.floor(Math.random() * value.length);
    r_char = value[t_char];
    value.splice(t_char, 1);
    target.val(value.join(''));
    // attack - removed char - position
    log(`remove_char-${r_char}-${t_char}`);
}


attacks = {
    'replace_char': replace_char,
    'flip_chars': flip_chars,
    'add_char': add_char,
    'remove_char': remove_char
}


$(document).ready(function() {
    
    var url = new URL(window.location.href);
    var mode = url.searchParams.get("atk_mode");
    var attack = url.searchParams.get("atk_type");
    
    if (mode == null) return;
    if (attack == 'random' || attack == null) attack = _.sample(Object.keys(attacks));
    
    $('#frameBox form').append(`<input type='hidden' name='atk_mode' value='${mode}'>`);
    $('#frameBox form').append(`<input type='hidden' name='atk_type' value='${attack}'>`);
    
    form_elems = $('input');
    text_elems_count = form_elems.toArray().reduce(function(a, b) { return a + (b.type == 'text'); }, 0) + $("textarea").length;
    
    if (mode == 'parallel' && text_elems_count < 2) log("Less than 2 text inputs -- not attacking");  
                  
    attack_modes[mode](attacks[attack]);

});
