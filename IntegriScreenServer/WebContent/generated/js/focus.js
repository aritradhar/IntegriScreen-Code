lastEditTimestamp = 0;        // track the last input event
focusTimestamp = 0;
minTimeFocusOutAfterEdit = 1000;     // minimum time to hold focus after last edit event
minTimeStayingFocused = 2000;       // minimu time that one element should be focused if it is edited

window.addEventListener("load", function () {  // add event listeners after the page loads
    console.log("Page loaded");
    let allInputs = document.forms[0].getElementsByTagName("input"); // register listener events for all input elements
    for (i = 0; i < allInputs.length; i++) {
        allInputs[i].addEventListener("input", updateLastEdit);
        allInputs[i].addEventListener("change", manageFocus);
        allInputs[i].addEventListener("focus", updateFocusTimestamp);
    }

    allInputs = document.forms[0].getElementsByTagName("textarea");  // register listener events for all textarea elements
    for (i = 0; i < allInputs.length; i++) {
        allInputs[i].addEventListener("input", updateLastEdit);
        allInputs[i].addEventListener("change", manageFocus);
        allInputs[i].addEventListener("focus", updateFocusTimestamp);
    }
});


function updateLastEdit() {           // update the timestamp of the last edit
    lastEditTimestamp = (new Date()).getTime();
}

function updateFocusTimestamp() {           // update the timestamp of element getting focused
    focusTimestamp = (new Date()).getTime();
}

function freezeJS(ms) {             // waste some time till limit is reached
    for (let i = 0; i < 1e7; i++) {
        if ((new Date().getTime() - lastEditTimestamp) > ms) {
            console.log("Delayed");
            break;
        }
    }
}

function manageFocus() {
    let diff = new Date().getTime() - lastEditTimestamp;
    if (diff < minTimeFocusOutAfterEdit) {  // check if minimum time after last edit has passed
        freezeJS(minTimeFocusOutAfterEdit - diff);  // freeze for few milliseconds
    }
    diff = new Date().getTime() - focusTimestamp;
    if (diff < minTimeStayingFocused) {
        freezeJS(minTimeStayingFocused - diff);
    }
}

$(document).ready(function () {
    $("<style>.no-pointer { cursor: none !important; }</style>").appendTo('head');
    $("<style>label, textarea, input, button {cursor: inherit;}</style>").appendTo('head');
    timer = null;
    $("body").mousemove(function () {
        clearTimeout(timer);
        $(this).removeClass("no-pointer");
        timer = setTimeout(function () {
            $('body').addClass("no-pointer");
        }, 1500);
    });

});
