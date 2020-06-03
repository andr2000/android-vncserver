#ifndef BUFFER_MANAGER_H_
#define BUFFER_MANAGER_H_

#include <memory>
#include <mutex>
#include <vector>

class VncGraphicBuffer;

class BufferManager {
public:
    enum MODE {
        TRIPLE,
        WITH_COMPARE
    };
    typedef VncGraphicBuffer *VncGraphicBufferPtr;

    BufferManager() = default;

    ~BufferManager();

    bool allocate(MODE mode, int width, int height, int format);

    void release();

    void
    getConsumer(VncGraphicBufferPtr &consumer, VncGraphicBufferPtr &compare);

    VncGraphicBuffer *getProducer();

private:
    MODE mMode{WITH_COMPARE};
    std::mutex mLock;
    std::vector<std::unique_ptr<VncGraphicBuffer>> mBuffers;

    int mConsumer{0};
    int mProducer{1};
    int mClean{2};
    int mCompare{3};
};

#endif /* BUFFER_MANAGER_H_ */
