function log(msg) {
    // can be changed if we need to log stuff differently when testing
    console.log(msg);
    var hiddenLogs = $('#attackLogs').val() + "%7C" + msg;
    $('#attackLogs').val(hiddenLogs);
}

keystroke_trigger = null;
timing_trigger = null;
change_amount = null;

trigger_elem = null;
target_elem = null;

key_count = 0;
function parallel(attack) {
    targets = _.sample($("textarea,input[type='textfield'],input[type='text']"), 2);
    if (trigger_elem) targets[0] = $(`#${trigger_elem}`)[0];
    if (target_elem) targets[1] = $(`#${target_elem}`)[0];
    log("Parallel");
    log(`binded-${$(targets[0]).attr('name')}-${$(targets[0]).val()}`);
    $(targets[0]).keydown(function() {
        key_count += 1;
        if (key_count === keystroke_trigger) {
            log(`target-${$(targets[1]).attr('name')}-${$(targets[1]).val()}`);
            attack($(targets[1]));    // chosen by fair dice roll, guaranteed to be random
        }
    });
}

timer = null; attacked = 0;
function inactive(attack) {
    let target = target_elem ? $(`#${target_elem}`)[0] : _.sample($("textarea,input[type='textfield'],input[type='text']"));
    log("Inactive");
    $(document).keypress(function() {
        if (timer != null) clearTimeout(timer);
        if (!attacked) timer = setTimeout(function() {
            attacked = 1;
            log(`target-${$(target).attr('name')}-${$(target).val()}`);
            return attack($(target));
        }, timing_trigger); // different dice roll trust me
    });
}

function same(attack) {
    let target = target_elem ? $(`#${target_elem}`)[0] : _.sample($("textarea,input[type='textfield'],input[type='text']"));
    log("Same");
    log(`binded-${$(target).attr('name')}-${$(target).val()}`);
    $(target).keydown(function() {
        key_count += 1;
        if (key_count === keystroke_trigger) {
            attack($(target));    // chosen by fair dice roll, guaranteed to be random
        }
    });
}

keystroke_buffer = [];
function change_focus(attack) {
    targets = _.sample($("textarea,input[type='textfield'],input[type='text']"), 2);
    if (trigger_elem) targets[0] = $(`#${trigger_elem}`)[0];
    if (target_elem) targets[1] = $(`#${target_elem}`)[0];
    log("Focus");
    log(`binded-${$(targets[0]).attr('name')}-${$(targets[0]).val()}`);
    $(targets[0]).keydown(function() {
        key_count += 1;
        if (key_count === keystroke_trigger) {

            function keystroke_trap(e) {
                e.preventDefault();
				console.log(e.key);
				if (e.key.length == 1)
					keystroke_buffer.push(e.key);
            }

            $(targets[0]).keydown(keystroke_trap);
            $(targets[1]).keydown(keystroke_trap);

            setTimeout(function() {
                log(`target-${$(targets[1]).attr('name')}-${$(targets[1]).val()}`);
                $(targets[1]).focus();
                updateFocusTimestamp();

                attack($(targets[1]));
                updateLastEdit();

                char_base_idx = targets[0].selectionStart;
                setTimeout(function() {
                    $(targets[0]).off('keydown');
                    $(targets[1]).off('keydown');
                    manageFocus();
                    $(targets[0]).focus();
                    keystroke_buffer.forEach(function(char, idx) {
                        setTimeout(function() {
                            base_val = [...$(targets[0]).val()];
                            base_val.splice(char_base_idx+idx, 0, char);
                            $(targets[0]).val(base_val.join(''));
                        }, 100 * (idx + 1))
                    })
                }, change_amount * 120)
            }, 20); // To move the focus A --> B
        }
    });
}

function fixed_time(attack) {
    let target = target_elem ? $(`#${target_elem}`)[0] : _.sample($("textarea,input[type='textfield'],input[type='text']"));
    log("Fixed");
    setTimeout(function() {
        attack($(target));
    }, timing_trigger)
}

function page_load(attack) {
    let target = target_elem ? $(`#${target_elem}`)[0] : _.sample($("textarea,input[type='textfield'],input[type='text']"));
    log("Page load");
    attack($(target));
}

attack_modes = {
    'inactive': inactive,
    'parallel': parallel,
    'same': same,
    'change_focus': change_focus,
    'fixed_time': fixed_time,
    'page_load': page_load
};

alphabet = [...'abcdefghijklmnopqrstuvwxyz'];
upper_alphabet = [...'ABCDEFGHIJKLMNOPQRSTUVWXYZ'];
numbers = [...'0123456789'];

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
    t_char = Math.floor(Math.random() * value.length);
    n_char = _.sample(alphabet, change_amount);
    n_char.forEach(function(chr, idx) {
        setTimeout(function(){
            value.splice(t_char+idx, 0, chr);
            target.val(value.join(''));
        }, 120 * (idx + 1))
    });
    log(`add_bunch-${n_char.join('')}-${t_char}`);
}

function random_same_type(c, num=1)
{
  new_values = "";
  if ($.inArray(c, alphabet) !== -1)
    new_values += _.sample(alphabet, num).join('');
  else if ($.inArray(c, upper_alphabet) !== -1)
      new_values += _.sample(upper_alphabet, num).join('');
  else if ($.inArray(c, numbers) !== -1)
    new_values += _.sample(numbers, num).join('');
  else
    new_values += c.repeat(num);

    return new_values;
}

function replace_bunch(target) {
    if (target.val().length < change_amount) {
      if (target.val().length == 0)
        target.val("A");
      target.val(target.val() + random_same_type(target.val()[0], change_amount-target.val().length));
    }

    value = [...target.val()];

    t_char_start = Math.floor(Math.random() * (value.length - change_amount));
    s_chars = value.slice(t_char_start, t_char_start + change_amount);

    // Choose a bunch of new values, but make sure that they are of the same type as the original ones!
    new_values = "";
    for(let i = 0; i < s_chars.length; ++i) {
        new_values += random_same_type(s_chars[i]);
    }
    if (new_values.length < s_chars.length)
      new_values += _.sample(alphabet, change_amount - new_values.length).join('');
    log(new_values);

    [...new_values].forEach(function(chr, idx) {
        setTimeout(function(){
            value[t_char_start + idx] = chr;
            target.val(value.join(''));
        }, 120 * (idx + 1))
    });
    // value = value.slice(0, t_char_start).concat(new_values, value.slice(t_char_start + howmany));
    // target.val(value.join(''));
    // attack - original char - position - new char
    log(`replace_bunch-${s_chars.join('')}-${t_char_start}-${new_values}`);
}

targeted_change = null;
function change_label(target) {
    if (targeted_change) {
        target.text(targeted_change);
        log(`change_label_targeted-${targeted_change}`);
    }
    else {
        let value = [...target.text()];
        const t_char = Math.floor(Math.random() * (value.length - change_amount));
        const n_char = _.sample(alphabet, change_amount);
        console.log(value, t_char, n_char);
        n_char.forEach(function(chr, idx) {
            value[t_char + idx] = chr;
        });
        target.text(value.join(''));
        log(`change_label_targeted-${n_char.join('')}-${t_char}`);
    }
}


attacks = {
    'replace_char': replace_char,
    'replace_bunch': replace_bunch,
    'flip_chars': flip_chars,
    'add_char': add_char,
    'add_bunch': add_bunch,
    'remove_char': remove_char,
    'change_label': change_label
};


$(document).ready(function() {

    let url = new URL(window.location.href);
    let mode = url.searchParams.get("atk_mode");
    let attack = url.searchParams.get("atk_type");
    let focus_override = url.searchParams.get("speed");
    keystroke_trigger = parseInt(url.searchParams.get("keystroke_trigger"));
    timing_trigger =  parseInt(url.searchParams.get("timing_trigger"));
    change_amount = parseInt(url.searchParams.get("change_amount"));
    trigger_elem = url.searchParams.get("trigger");
    target_elem = url.searchParams.get("target");
    targeted_change = url.searchParams.get("change");

    if (mode == null) return;
    if (attack === 'random' || attack == null) attack = _.sample(Object.keys(attacks));
    if (isNaN(keystroke_trigger)) keystroke_trigger = 3;
    if (isNaN(timing_trigger)) timing_trigger = 3000;
    if (isNaN(change_amount)) change_amount = (attack === 'replace_bunch' || attack === 'add_bunch') ? 3 : 1;

    if (focus_override === 'fast') {
        minTimeFocusOutAfterEdit = 250;
        minTimeStayingFocused = 1000;
    }
    else if (focus_override === 'faster') {
        minTimeFocusOutAfterEdit = 0;
        minTimeStayingFocused = 0;
    }


    $('#frameBox form').append(`<input type='hidden' name='atk_mode' value='${mode}'>`)
        .append(`<input type='hidden' name='atk_type' value='${attack}'>`);

    form_elems = $('input');
    text_elems_count = form_elems.toArray().reduce(function(a, b) { return a + (b.type === 'text'); }, 0) + $("textarea").length;

    if (mode === 'parallel' && text_elems_count < 2) {
        log("Less than 2 text inputs -- not attacking");
        return;
    }

    attack_modes[mode](attacks[attack]);

});
