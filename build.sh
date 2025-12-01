#!/bin/bash
set -e

echo "🚀 SpotLiveScreen Player Android - Build Script"
echo "================================================"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Step 1: Build Go Backend
echo -e "\n${BLUE}Step 1: Building Go backend for Android ARM64...${NC}"
cd backend

if ! command -v go &> /dev/null; then
    echo -e "${RED}Error: Go is not installed${NC}"
    exit 1
fi

echo "Downloading Go dependencies..."
go mod tidy

echo "Building for Android ARM64..."
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build -ldflags="-s -w" -o spotlive-server-arm64 cmd/main.go

if [ ! -f "spotlive-server-arm64" ]; then
    echo -e "${RED}Error: Failed to build Go server${NC}"
    exit 1
fi

FILE_SIZE=$(ls -lh spotlive-server-arm64 | awk '{print $5}')
echo -e "${GREEN}✓ Go server built: spotlive-server-arm64 ($FILE_SIZE)${NC}"

cd ..

# Step 2: Build Frontend PWA
echo -e "\n${BLUE}Step 2: Building React frontend...${NC}"
cd webapp

if ! command -v npm &> /dev/null; then
    echo -e "${RED}Error: npm is not installed${NC}"
    exit 1
fi

if [ ! -d "node_modules" ]; then
    echo "Installing npm dependencies..."
    npm install
fi

echo "Building PWA..."
npm run build

if [ ! -d "dist" ]; then
    echo -e "${RED}Error: Frontend build failed${NC}"
    exit 1
fi

DIST_SIZE=$(du -sh dist | awk '{print $1}')
echo -e "${GREEN}✓ Frontend built: webapp/dist ($DIST_SIZE)${NC}"

cd ..

# Step 3: Embed frontend in Go backend
echo -e "\n${BLUE}Step 3: Embedding frontend in Go backend...${NC}"
mkdir -p backend/cmd/webapp
cp -r webapp/dist backend/cmd/webapp/
echo -e "${GREEN}✓ Frontend embedded${NC}"

# Step 4: Copy Go binary to Android assets
echo -e "\n${BLUE}Step 4: Copying binaries to Android assets...${NC}"
mkdir -p android/app/src/main/assets
cp backend/spotlive-server-arm64 android/app/src/main/assets/spotlive-server
chmod +x android/app/src/main/assets/spotlive-server
echo -e "${GREEN}✓ Binary copied to Android assets${NC}"

# Step 5: Build Android APK
echo -e "\n${BLUE}Step 5: Building Android APK...${NC}"
cd android

if [ ! -f "gradlew" ]; then
    echo -e "${RED}Error: gradlew not found. Run this in Android Studio first.${NC}"
    exit 1
fi

chmod +x gradlew

echo "Building debug APK..."
./gradlew assembleDebug

if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    APK_SIZE=$(ls -lh app/build/outputs/apk/debug/app-debug.apk | awk '{print $5}')
    echo -e "${GREEN}✓ Debug APK built: $APK_SIZE${NC}"
    echo -e "${GREEN}  Location: android/app/build/outputs/apk/debug/app-debug.apk${NC}"
fi

echo ""
echo "Building release APK..."
./gradlew assembleRelease

if [ -f "app/build/outputs/apk/release/app-release-unsigned.apk" ]; then
    APK_SIZE=$(ls -lh app/build/outputs/apk/release/app-release-unsigned.apk | awk '{print $5}')
    echo -e "${GREEN}✓ Release APK built: $APK_SIZE${NC}"
    echo -e "${GREEN}  Location: android/app/build/outputs/apk/release/app-release-unsigned.apk${NC}"
fi

cd ..

# Done
echo -e "\n${GREEN}================================================${NC}"
echo -e "${GREEN}✓ Build completed successfully!${NC}"
echo -e "${GREEN}================================================${NC}"
echo ""
echo "Next steps:"
echo "1. Install APK: adb install android/app/build/outputs/apk/debug/app-debug.apk"
echo "2. Or copy APK to device and install manually"
echo ""
