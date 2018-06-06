lastEditTimestamp = 0;        // track the last input event
minTimeFocusOutAfterEdit = 500;     // minimum time to hold focus after last edit event

window.addEventListener("load", function() {  // add event listeners after the page loads
  console.log("Page loaded");
  var allInputs = document.forms[0].getElementsByTagName("input") // register listener events for all input elements
  for (i = 0; i < allInputs.length; i++) {
    allInputs[i].addEventListener("input", updateLastEdit);
    allInputs[i].addEventListener("change", manageFocus);
  }

  var allInputs = document.forms[0].getElementsByTagName("textarea")  // register listener events for all textarea elements
  for (i = 0; i < allInputs.length; i++) {
    allInputs[i].addEventListener("input", updateLastEdit);
    allInputs[i].addEventListener("change", manageFocus);
  }
});


function updateLastEdit() {           // update the timestamp of the last edit
  lastEditTimestamp = (new Date()).getTime();
}

function freezeJS(ms) {             // waste some time till limit is reached
  for (var i = 0; i < 1e7; i++) {
    if ((new Date().getTime() - lastEditTimestamp) > ms){
      console.log("Delayed");
      break;
    }
  }
}

function manageFocus() {
    var diff = new Date().getTime() - lastEditTimestamp;
    if (diff < minTimeFocusOutAfterEdit) {  // check if minimum time has passed
      freezeJS(minTimeFocusOutAfterEdit - diff);  // freeze for few milliseconds
    }
}