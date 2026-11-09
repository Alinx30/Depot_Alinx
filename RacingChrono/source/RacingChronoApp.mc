using Toybox.Application as App;

class RacingChronoApp extends App.AppBase {

    function initialize() {
        App.AppBase.initialize();
    }

    function onStart(state) {
    }

    function onStop(state) {
    }

    function getInitialView() {
        return [ new RacingChronoView() ];
    }
}
