import fs from 'node:fs';
import {pathToFileURL} from 'node:url';
import path from 'node:path';
import {createHash} from 'node:crypto';

const productionOrigin='https://classforge-tiago-15124.eastus.cloudapp.azure.com';

export function normalizeFingerprint(value) {
  if(typeof value!=='string' || !/^(?:[a-f\d]{64}|(?:[a-f\d]{2}:){31}[a-f\d]{2})$/i.test(value.trim())) {
    throw new Error('Expected a complete SHA256 signing certificate fingerprint');
  }
  return value.trim().replaceAll(':','').toUpperCase().match(/../g).join(':');
}

export function createAssetLinks(packageName, fingerprint) {
  if(typeof packageName!=='string' || !/^[a-z][a-z\d_]*(?:\.[a-z][a-z\d_]*)+$/.test(packageName)) {
    throw new Error('Invalid Android package name');
  }
  return [{relation:['delegate_permission/common.handle_all_urls'],target:{
    namespace:'android_app',package_name:packageName,
    sha256_cert_fingerprints:[normalizeFingerprint(fingerprint)]
  }}];
}

export function verifyAssetLinks(document, packageName, fingerprint) {
  const expected=createAssetLinks(packageName,fingerprint);
  if(JSON.stringify(document)!==JSON.stringify(expected)) {
    throw new Error('Asset links do not match this release package and signer');
  }
  return true;
}

export function validateRelease(directory) {
  for(const file of ['classforge.apk','classforge.apk.sha256','assetlinks.json','release.json']) {
    if(!fs.existsSync(path.join(directory,file)) || !fs.statSync(path.join(directory,file)).isFile()) {
      throw Error(`Release file missing: ${file}`);
    }
  }
  const metadata=JSON.parse(fs.readFileSync(path.join(directory,'release.json'),'utf8'));
  if(metadata.origin!==productionOrigin)throw Error('Release origin must match production HTTPS');
  if(metadata.packageName!=='com.classforge.app')throw Error('Release package must be com.classforge.app');
  const hash=createHash('sha256').update(fs.readFileSync(path.join(directory,'classforge.apk'))).digest('hex');
  const checksum=fs.readFileSync(path.join(directory,'classforge.apk.sha256'),'utf8').trim();
  if(metadata.apkSha256!==hash || checksum!==`${hash}  classforge.apk`)throw Error('APK checksum mismatch');
  verifyAssetLinks(JSON.parse(fs.readFileSync(path.join(directory,'assetlinks.json'),'utf8')),metadata.packageName,metadata.signingCertificateSha256);
  return metadata;
}

if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  try {
    const [action,packageName,fingerprint,file]=process.argv.slice(2);
    if(action==='validate') {validateRelease(packageName);process.exit(0);}
    if(!file)throw Error('Usage: android-release.mjs <create|verify> <package> <SHA256> <assetlinks.json>');
    if(action==='create')fs.writeFileSync(file,JSON.stringify(createAssetLinks(packageName,fingerprint),null,2)+'\n');
    else if(action==='verify')verifyAssetLinks(JSON.parse(fs.readFileSync(file,'utf8')),packageName,fingerprint);
    else throw Error('Unknown action');
  } catch(error) {console.error(error.message);process.exitCode=1;}
}
