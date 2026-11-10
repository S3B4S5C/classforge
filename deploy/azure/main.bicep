targetScope = 'resourceGroup'

@description('Globally unique App Service name; use a dedicated plan for the single backend instance.')
@minLength(3)
@maxLength(60)
param appName string
param location string = resourceGroup().location
@allowed(['B1', 'B2', 'B3'])
param sku string = 'B1'

@secure()
@minLength(64)
param jwtSecret string
@secure()
@minLength(1)
param mongoUri string
@secure()
@minLength(1)
param groqApiKey string

param groqTextModel string = 'openai/gpt-oss-120b'
param groqVisionModel string = 'qwen/qwen3.8-27b'
param groqSpeechModel string = 'whisper-large-v3-turbo'
@description('Additional HTTPS origins for custom domains. The actual Azure hostname is included automatically.')
param additionalAllowedOrigins array = []

resource plan 'Microsoft.Web/serverfarms@2024-11-01' = {
  name: '${appName}-plan'
  location: location
  kind: 'linux'
  sku: { name: sku, tier: 'Basic', capacity: 1 }
  properties: { reserved: true, perSiteScaling: false }
}

resource webApp 'Microsoft.Web/sites@2024-11-01' = {
  name: appName
  location: location
  kind: 'app,linux'
  identity: { type: 'SystemAssigned' }
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    clientAffinityEnabled: true
    siteConfig: {
      linuxFxVersion: 'JAVA|21-java21'
      alwaysOn: true
      ftpsState: 'Disabled'
      minTlsVersion: '1.2'
      scmMinTlsVersion: '1.2'
      http20Enabled: true
      healthCheckPath: '/api/health'
      remoteDebuggingEnabled: false
    }
  }
}

resource settings 'Microsoft.Web/sites/config@2024-11-01' = {
  parent: webApp
  name: 'appsettings'
  properties: {
    SPRING_PROFILES_ACTIVE: 'azure'
    CLASSFORGE_MONGODB_URI: mongoUri
    CLASSFORGE_JWT_SECRET: jwtSecret
    CLASSFORGE_WEBSOCKET_ALLOWED_ORIGINS: join(concat(['https://${webApp.properties.defaultHostName}'], additionalAllowedOrigins), ',')
    GROQ_API_KEY: groqApiKey
    GROQ_TEXT_MODEL: groqTextModel
    GROQ_VISION_MODEL: groqVisionModel
    GROQ_SPEECH_MODEL: groqSpeechModel
    JAVA_OPTS: '-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError --enable-native-access=ALL-UNNAMED'
    WEBSITE_WARMUP_PATH: '/api/health'
    SCM_DO_BUILD_DURING_DEPLOYMENT: 'false'
  }
}

resource ftpPolicy 'Microsoft.Web/sites/basicPublishingCredentialsPolicies@2024-11-01' = {
  parent: webApp
  name: 'ftp'
  properties: { allow: false }
}
resource scmPolicy 'Microsoft.Web/sites/basicPublishingCredentialsPolicies@2024-11-01' = {
  parent: webApp
  name: 'scm'
  properties: { allow: false }
}

output appUrl string = 'https://${webApp.properties.defaultHostName}'
output webAppName string = webApp.name
output outboundIpAddresses string = webApp.properties.outboundIpAddresses
output possibleOutboundIpAddresses string = webApp.properties.possibleOutboundIpAddresses
output managedIdentityPrincipalId string = webApp.identity.principalId
