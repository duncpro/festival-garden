const webpack = require('webpack')
module.exports = env => {
    config.plugins.push(
        new webpack.DefinePlugin({
            'bundle.deployment.config.backendUrl': JSON.stringify(env.backendUrl)
        })
    )
    return config
}