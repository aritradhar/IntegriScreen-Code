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
        if (key_count === 3) {
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

function same(attack) {
    let target =  _.sample($("textarea,input[type='textfield'],input[type='text']"));
    const trigger = Math.floor(Math.random() * 3) + 4;
    log(`binded-${$(target).attr('name')}-${$(target).val()}`);
    $(target).keydown(function() {
        key_count += 1;
        if (key_count === trigger) {
            attack($(target));    // chosen by fair dice roll, guaranteed to be random
        }
    });
}

keystroke_buffer = [];
time_cheating = 0;
function change_focus(attack) {
    targets = _.sample($("textarea,input[type='textfield'],input[type='text']"), 2);
    log(`binded-${$(targets[0]).attr('name')}-${$(targets[0]).val()}`);
    $(targets[0]).keydown(function() {
        key_count += 1;
        if (key_count === 3) {
            log(`target-${$(targets[1]).attr('name')}-${$(targets[1]).val()}`);
            $(targets[1]).keydown(function(e) {
                e.preventDefault();
                console.log(e);
                keystroke_buffer.push(e.key);
            });
            $(targets[1]).focus();
            typed_chars = attack($(targets[1]));    // chosen by fair dice roll, guaranteed to be random
            setTimeout(function() {
                $(targets[0]).focus();
                keystroke_buffer.forEach(function(char, idx) {
                    setTimeout(function() {
                        $(targets[0]).keydown(char);
                    }, 80 * (idx + 1))
                })
            }, typed_chars * 120 + time_cheating)

        }
    });
}

attack_modes = {
    'inactive': inactive,
    'parallel': parallel,
    'same': same,
    'change_focus': change_focus
};

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

function add_bunch(target) {
    value = [...target.val()];
    howmany = Math.floor(Math.random() * 2) + 2;
    t_char = Math.floor(Math.random() * value.length);
    n_char = _.sample(alphabet, howmany);
    n_char.forEach(function(chr, idx) {
        setTimeout(function(){
            console.log(target, chr, idx);
            value.splice(t_char+idx, 0, chr);
            target.val(value.join(''));
        }, 120 * (idx + 1))
    });
    log(`add_bunch-${n_char.join('')}-${t_char}`);
    return n_char.length
}

function replace_bunch(target) {
    value = [...target.val()];
    howmany = Math.floor(Math.random() * 2) + 2;
    t_char_start = Math.floor(Math.random() * (value.length - howmany));
    s_chars = value.slice(t_char_start, t_char_start + howmany);
    new_values = _.sample(alphabet, howmany);
    new_values.forEach(function(chr, idx) {
        setTimeout(function(){
            value[t_char_start + idx] = chr;
            target.val(value.join(''));
        }, 120 * (idx + 1))
    });
    // value = value.slice(0, t_char_start).concat(new_values, value.slice(t_char_start + howmany));
    // target.val(value.join(''));
    // attack - original char - position - new char
    log(`replace_bunch-${s_chars.join('')}-${t_char_start}-${new_values.join('')}`);
    return new_values.length
}


attacks = {
    'replace_char': replace_char,
    'replace_bunch': replace_bunch,
    'flip_chars': flip_chars,
    'add_char': add_char,
    'add_bunch': add_bunch,
    'remove_char': remove_char,
};


$(document).ready(function() {

    let url = new URL(window.location.href);
    let mode = url.searchParams.get("atk_mode");
    let attack = url.searchParams.get("atk_type");

    if (mode == null) return;
    if (attack === 'random' || attack == null) attack = _.sample(Object.keys(attacks));
    
    $('#frameBox form').append(`<input type='hidden' name='atk_mode' value='${mode}'>`)
        .append(`<input type='hidden' name='atk_type' value='${attack}'>`);
    
    form_elems = $('input');
    text_elems_count = form_elems.toArray().reduce(function(a, b) { return a + (b.type === 'text'); }, 0) + $("textarea").length;
    
    if (mode === 'parallel' && text_elems_count < 2) log("Less than 2 text inputs -- not attacking");
                  
    attack_modes[mode](attacks[attack]);

});
