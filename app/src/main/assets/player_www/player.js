ExternalCommunicationManager = function() {
    return {
        config: {},
        setConfig: function(config) {
            this.config = config;
            if (this.config.opt1)
                $('input:radio[name=opt1][value='+this.config.opt1+']').prop('checked', 'checked');

            if (this.config.opt2)
                $('input:radio[name=opt2][value='+this.config.opt2+']').prop('checked', 'checked');
        },
        setConfigValue: function(key, val) {
            this.config[key] = val;
        },
        getConfig: function() {
            return this.config;
        },
        currentState: {},
        getCurrentState: function() {
            return this.currentState;
        },
        setCurrentState: function(currentState) {
            this.currentState = currentState;
            $('#state1').html(this.currentState.state1);
            $('#state2').html(this.currentState.state2);
        }
    };
};
