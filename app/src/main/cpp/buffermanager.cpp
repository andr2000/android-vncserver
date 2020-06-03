#include "buffermanager.h"
#include "log.h"
#include "vncgraphicbuffer.h"

BufferManager::~BufferManager() {
    release();
}

bool BufferManager::allocate(BufferManager::MODE mode, int width, int height,
                             int format) {
    mMode = mode;
    int n = 0;
    n = mMode == TRIPLE ? 3 : 4;
    for (int i = 0; i < n; i++) {
        /* allocate buffer */
        std::unique_ptr<VncGraphicBuffer> buffer(
                new VncGraphicBuffer(width, height, format));
        mBuffers.push_back(std::move(buffer));
        if (!mBuffers[i]->allocate()) {
            return false;
        }
    }
    return true;
}

void BufferManager::release() {
    mBuffers.clear();
}

void BufferManager::getConsumer(VncGraphicBufferPtr &consumer,
                                VncGraphicBufferPtr &compare) {
    std::lock_guard<std::mutex> lock(mLock);
    if (mMode == WITH_COMPARE) {
        /* clean -> consumer, consumer -> compare, compare -> clean */
        int cleanIdx = mClean;
        int consumerIdx = mConsumer;
        int compareIdx = mCompare;
        mConsumer = cleanIdx;
        mCompare = consumerIdx;
        mClean = compareIdx;
        compare = mBuffers[mCompare].get();
    } else {
        /* clean -> consumer, consumer -> clean */
        int cleanIdx = mClean;
        int consumerIdx = mConsumer;
        mConsumer = cleanIdx;
        mClean = consumerIdx;
        compare = nullptr;
    }
    consumer = mBuffers[mConsumer].get();
}

VncGraphicBuffer *BufferManager::getProducer() {
    std::lock_guard<std::mutex> lock(mLock);
    int cleanIdx = mClean;
    int producerIdx = mProducer;
    mClean = producerIdx;
    mProducer = cleanIdx;
    return mBuffers[mProducer].get();
}
