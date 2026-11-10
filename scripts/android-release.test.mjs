import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {createAssetLinks, normalizeFingerprint, verifyAssetLinks, validateRelease} from './android-release.mjs';

const compact='ab'.repeat(32);
const colon=Array(32).fill('AB').join(':');
test('release fingerprint accepts apksigner digest and keytool colon notation',()=>{
  assert.equal(normalizeFingerprint(compact),colon);
  assert.equal(normalizeFingerprint(colon),colon);
});
test('release fingerprint rejects truncated, nonhex and ambiguous values',()=>{
  for(const bad of ['', 'ab', 'xz'.repeat(32), 'ab:'.repeat(32), 'ab-'.repeat(32), null]) {
    assert.throws(()=>normalizeFingerprint(bad),/SHA256/);
  }
});
test('asset links binds exactly one release package and certificate',()=>{
  assert.deepEqual(createAssetLinks('com.classforge.app',compact),[{
    relation:['delegate_permission/common.handle_all_urls'],
    target:{namespace:'android_app',package_name:'com.classforge.app',sha256_cert_fingerprints:[colon]}
  }]);
  assert.equal(verifyAssetLinks(createAssetLinks('com.classforge.app',compact),'com.classforge.app',compact),true);
});
test('asset links refuses invalid Android package names',()=>{
  for(const bad of ['', 'classforge', 'com.*', 'com/classforge/app', 'com.classforge.app;touch', null]) {
    assert.throws(()=>createAssetLinks(bad,compact),/package/);
  }
});
test('publish rejects wrong signer, different package or extra delegation',()=>{
  const valid=createAssetLinks('com.classforge.app',compact);
  assert.throws(()=>verifyAssetLinks(valid,'com.classforge.app','cd'.repeat(32)),/match/);
  assert.throws(()=>verifyAssetLinks(valid,'com.example.other',compact),/match/);
  assert.throws(()=>verifyAssetLinks([...valid,...valid],'com.classforge.app',compact),/match/);
  const extra=structuredClone(valid); extra[0].relation.push('delegate_permission/common.get_login_creds');
  assert.throws(()=>verifyAssetLinks(extra,'com.classforge.app',compact),/match/);
});

test('publish preflight rejects incomplete releases, tampered APK and a foreign origin',()=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'classforge-release-'));
  try {
    assert.throws(()=>validateRelease(dir),/missing/i);
    const apk=Buffer.from('release-artifact-fixture');
    const sha=createHash('sha256').update(apk).digest('hex');
    fs.writeFileSync(path.join(dir,'classforge.apk'),apk);
    fs.writeFileSync(path.join(dir,'classforge.apk.sha256'),sha+'  classforge.apk\n');
    const metadata={packageName:'com.classforge.app',origin:'https://classforge-tiago-15124.eastus.cloudapp.azure.com',apkSha256:sha,signingCertificateSha256:compact};
    fs.writeFileSync(path.join(dir,'assetlinks.json'),JSON.stringify(createAssetLinks(metadata.packageName,compact)));
    fs.writeFileSync(path.join(dir,'release.json'),JSON.stringify(metadata));
    assert.equal(validateRelease(dir).apkSha256,sha);
    fs.appendFileSync(path.join(dir,'classforge.apk'),'tampered');
    assert.throws(()=>validateRelease(dir),/checksum/i);
    fs.writeFileSync(path.join(dir,'classforge.apk'),apk);
    fs.writeFileSync(path.join(dir,'release.json'),JSON.stringify({...metadata,origin:'https://attacker.example'}));
    assert.throws(()=>validateRelease(dir),/origin/i);
    fs.writeFileSync(path.join(dir,'release.json'),JSON.stringify({...metadata,signingCertificateSha256:'cd'.repeat(32)}));
    assert.throws(()=>validateRelease(dir),/match/);
  } finally { fs.rmSync(dir,{recursive:true,force:true}); }
});
