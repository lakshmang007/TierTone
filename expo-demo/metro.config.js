// Learn more: https://docs.expo.dev/guides/customizing-metro
const { getDefaultConfig } = require('expo/metro-config')
const path = require('path')

const config = getDefaultConfig(__dirname)

// Share the real app's types and rule helpers (../src/model.ts) instead of copying them.
config.watchFolders = [path.resolve(__dirname, '../src')]

module.exports = config
