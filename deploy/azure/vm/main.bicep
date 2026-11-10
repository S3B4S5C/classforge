targetScope = 'resourceGroup'

@minLength(3)
@maxLength(40)
param vmName string = 'classforge-vm'
param location string = resourceGroup().location
param vmSize string = 'Standard_B2s'
param adminUsername string = 'azureuser'
@description('SSH PUBLIC key only; never pass the private key.')
@minLength(40)
param sshPublicKey string
@description('Administrative public IPv4 without a CIDR suffix. SSH is restricted to this /32.')
param adminPublicIp string
@description('Unique DNS label in the Azure region. Read the actual FQDN from outputs.')
param dnsLabel string

resource publicIp 'Microsoft.Network/publicIPAddresses@2024-05-01' = {
  name: '${vmName}-ip'
  location: location
  sku: { name: 'Standard' }
  properties: {
    publicIPAllocationMethod: 'Static'
    publicIPAddressVersion: 'IPv4'
    dnsSettings: { domainNameLabel: dnsLabel }
  }
}

resource nsg 'Microsoft.Network/networkSecurityGroups@2024-05-01' = {
  name: '${vmName}-nsg'
  location: location
  properties: {
    securityRules: [
      {
        name: 'SSH-Admin-Only'
        properties: {
          priority: 100, direction: 'Inbound', access: 'Allow', protocol: 'Tcp'
          sourcePortRange: '*', destinationPortRange: '22'
          sourceAddressPrefix: '${adminPublicIp}/32', destinationAddressPrefix: '*'
        }
      }
      {
        name: 'HTTP-ACME'
        properties: {
          priority: 110, direction: 'Inbound', access: 'Allow', protocol: 'Tcp'
          sourcePortRange: '*', destinationPortRange: '80'
          sourceAddressPrefix: 'Internet', destinationAddressPrefix: '*'
        }
      }
      {
        name: 'HTTPS-WSS'
        properties: {
          priority: 120, direction: 'Inbound', access: 'Allow', protocol: 'Tcp'
          sourcePortRange: '*', destinationPortRange: '443'
          sourceAddressPrefix: 'Internet', destinationAddressPrefix: '*'
        }
      }
    ]
  }
}

resource vnet 'Microsoft.Network/virtualNetworks@2024-05-01' = {
  name: '${vmName}-vnet'
  location: location
  properties: {
    addressSpace: { addressPrefixes: ['10.40.0.0/16'] }
    subnets: [
      {
        name: 'application'
        properties: { addressPrefix: '10.40.0.0/24', defaultOutboundAccess: false }
      }
    ]
  }
}

resource nic 'Microsoft.Network/networkInterfaces@2024-05-01' = {
  name: '${vmName}-nic'
  location: location
  properties: {
    networkSecurityGroup: { id: nsg.id }
    ipConfigurations: [
      {
        name: 'primary'
        properties: {
          privateIPAllocationMethod: 'Dynamic'
          subnet: { id: vnet.properties.subnets[0].id }
          publicIPAddress: { id: publicIp.id }
        }
      }
    ]
  }
}

resource vm 'Microsoft.Compute/virtualMachines@2024-07-01' = {
  name: vmName
  location: location
  properties: {
    hardwareProfile: { vmSize: vmSize }
    securityProfile: {
      securityType: 'TrustedLaunch'
      uefiSettings: { secureBootEnabled: true, vTpmEnabled: true }
    }
    osProfile: {
      computerName: vmName
      adminUsername: adminUsername
      linuxConfiguration: {
        disablePasswordAuthentication: true
        provisionVMAgent: true
        ssh: {
          publicKeys: [
            { path: '/home/${adminUsername}/.ssh/authorized_keys', keyData: sshPublicKey }
          ]
        }
      }
    }
    storageProfile: {
      imageReference: { publisher: 'Canonical', offer: 'ubuntu-24_04-lts', sku: 'server', version: 'latest' }
      osDisk: {
        createOption: 'FromImage'
        diskSizeGB: 32
        managedDisk: { storageAccountType: 'StandardSSD_LRS' }
      }
    }
    networkProfile: { networkInterfaces: [{ id: nic.id }] }
  }
}

output vmResourceName string = vm.name
output ipAddress string = publicIp.properties.ipAddress
output fqdn string = publicIp.properties.dnsSettings.fqdn
output sshCommand string = 'ssh ${adminUsername}@${publicIp.properties.dnsSettings.fqdn}'
