#!/bin/bash

# ==========================================
# CDN Platform Build Script
# Builds all Rust services and Java fat JARs
# ==========================================

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$PROJECT_ROOT/build"

echo "==========================================
CDN Platform Build Script
=========================================="
echo "Project root: $PROJECT_ROOT"
echo "Build output: $BUILD_DIR"
echo ""

# Create build directory
mkdir -p "$BUILD_DIR"

# ==========================================
# Build Rust Services
# ==========================================

echo "==========================================
Building Rust Services
=========================================="

# Build upload-service
echo ""
echo "--- Building upload-service (Rust) ---"
cd "$PROJECT_ROOT/upload-service"
if [ -f "Cargo.toml" ]; then
    echo "Building in Docker container for GLIBC compatibility..."
    # Clean Cargo.lock to regenerate with compatible versions
    rm -f Cargo.lock
    docker run --rm \
        -v "$PROJECT_ROOT/upload-service:/workspace" \
        -w /workspace \
        rust:latest \
        cargo build --release
    cp target/release/upload_service "$BUILD_DIR/upload_service"
    echo "✅ upload-service built successfully"
    echo "   Binary: $BUILD_DIR/upload_service"
else
    echo "❌ upload-service Cargo.toml not found"
fi

# Build processing-worker
echo ""
echo "--- Building processing-worker (Rust) ---"
cd "$PROJECT_ROOT/processing-worker"
if [ -f "Cargo.toml" ]; then
    echo "Building in Docker container for GLIBC compatibility..."
    # Clean Cargo.lock to regenerate with compatible versions
    rm -f Cargo.lock
    docker run --rm \
        -v "$PROJECT_ROOT/processing-worker:/workspace" \
        -w /workspace \
        rust:latest \
        cargo build --release
    cp target/release/processing_worker "$BUILD_DIR/processing_worker"
    echo "✅ processing-worker built successfully"
    echo "   Binary: $BUILD_DIR/processing_worker"
else
    echo "❌ processing-worker Cargo.toml not found"
fi

# Build streaming-service (if exists)
echo ""
echo "--- Building streaming-service (Rust) ---"
if [ -d "$PROJECT_ROOT/streaming-service" ]; then
    cd "$PROJECT_ROOT/streaming-service"
    if [ -f "Cargo.toml" ]; then
        echo "Building in Docker container for GLIBC compatibility..."
        # Clean Cargo.lock to regenerate with compatible versions
        rm -f Cargo.lock
        docker run --rm \
            -v "$PROJECT_ROOT/streaming-service:/workspace" \
            -w /workspace \
            rust:latest \
            cargo build --release
        cp target/release/streaming_service "$BUILD_DIR/streaming_service"
        echo "✅ streaming-service built successfully"
        echo "   Binary: $BUILD_DIR/streaming_service"
    else
        echo "⚠️  streaming-service directory exists but no Cargo.toml found"
    fi
else
    echo "⚠️  streaming-service not yet created"
fi

# ==========================================
# Build Java Services
# ==========================================

echo ""
echo "==========================================
Building Java Services
=========================================="

# Build video-service
echo ""
echo "--- Building video-service (Java) ---"
if [ -d "$PROJECT_ROOT/video-service" ]; then
    cd "$PROJECT_ROOT/video-service"
    if [ -f "pom.xml" ]; then
        ./mvnw clean package -DskipTests
        cp target/*.jar "$BUILD_DIR/video-service.jar" 2>/dev/null || echo "⚠️  No JAR found (may not be implemented yet)"
        echo "✅ video-service built successfully"
        echo "   JAR: $BUILD_DIR/video-service.jar"
    elif [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then
        ./gradlew clean bootJar
        cp build/libs/*.jar "$BUILD_DIR/video-service.jar" 2>/dev/null || echo "⚠️  No JAR found (may not be implemented yet)"
        echo "✅ video-service built successfully"
        echo "   JAR: $BUILD_DIR/video-service.jar"
    else
        echo "⚠️  video-service not yet implemented (no pom.xml or build.gradle)"
    fi
else
    echo "⚠️  video-service directory not found"
fi

# Build job-service
echo ""
echo "--- Building job-service (Java) ---"
if [ -d "$PROJECT_ROOT/job-service" ]; then
    cd "$PROJECT_ROOT/job-service"
    if [ -f "pom.xml" ]; then
        ./mvnw clean package -DskipTests
        cp target/*.jar "$BUILD_DIR/job-service.jar" 2>/dev/null || echo "⚠️  No JAR found (may not be implemented yet)"
        echo "✅ job-service built successfully"
        echo "   JAR: $BUILD_DIR/job-service.jar"
    elif [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then
        ./gradlew clean bootJar
        cp build/libs/*.jar "$BUILD_DIR/job-service.jar" 2>/dev/null || echo "⚠️  No JAR found (may not be implemented yet)"
        echo "✅ job-service built successfully"
        echo "   JAR: $BUILD_DIR/job-service.jar"
    else
        echo "⚠️  job-service not yet implemented (no pom.xml or build.gradle)"
    fi
else
    echo "⚠️  job-service directory not found"
fi

# Build analytics-service
echo ""
echo "--- Building analytics-service (Java) ---"
if [ -d "$PROJECT_ROOT/analytics-service" ]; then
    cd "$PROJECT_ROOT/analytics-service"
    if [ -f "pom.xml" ]; then
        ./mvnw clean package -DskipTests
        cp target/*.jar "$BUILD_DIR/analytics-service.jar" 2>/dev/null || echo "⚠️  No JAR found (may not be implemented yet)"
        echo "✅ analytics-service built successfully"
        echo "   JAR: $BUILD_DIR/analytics-service.jar"
    elif [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then
        ./gradlew clean bootJar
        cp build/libs/*.jar "$BUILD_DIR/analytics-service.jar" 2>/dev/null || echo "⚠️  No JAR found (may not be implemented yet)"
        echo "✅ analytics-service built successfully"
        echo "   JAR: $BUILD_DIR/analytics-service.jar"
    else
        echo "⚠️  analytics-service not yet implemented (no pom.xml or build.gradle)"
    fi
else
    echo "⚠️  analytics-service directory not found"
fi

# ==========================================
# Build Summary
# ==========================================

echo ""
echo "==========================================
Build Summary
=========================================="
echo "Built artifacts in: $BUILD_DIR"
echo ""
ls -lh "$BUILD_DIR" 2>/dev/null || echo "No artifacts found"

echo ""
echo "==========================================
Build Complete!
=========================================="
echo ""
echo "Next steps:"
echo "  1. Run: docker compose build"
echo "  2. Run: docker compose up -d"
echo ""
