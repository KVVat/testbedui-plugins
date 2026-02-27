#!/bin/bash
# apps/appupdate/scripts/generate_test_apks.sh

BUILD_DIR=$1
TARGET_DIR=$2

echo "Organizing APKs for CC testing..."

# 1. V1 APK
cp "${BUILD_DIR}/outputs/apk/v1/debug/appupdate-v1-debug.apk" "${TARGET_DIR}/appupdate-v1.apk"

# 2. V2 APK (Higher Version)
cp "${BUILD_DIR}/outputs/apk/v2/debug/appupdate-v2-debug.apk" "${TARGET_DIR}/appupdate-v2.apk"

# 3. Mismatched Signature APK (Same Version as V1 but different key)
cp "${BUILD_DIR}/outputs/apk/mismatched/debug/appupdate-mismatched-debug.apk" "${TARGET_DIR}/appupdate-mismatched.apk"

# 4. Unsigned APK (Releaseビルドの署名なしを使用)
cp "${BUILD_DIR}/outputs/apk/v1/release/appupdate-v1-release-unsigned.apk" "${TARGET_DIR}/appupdate-unsigned.apk"

echo "✅ APKs are ready in ${TARGET_DIR}"