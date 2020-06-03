#include "buffermanager.h"
#include "log.h"
#include "vncgraphicbuffer.h"

BufferManager::~BufferManager()
{
	release();
}

bool BufferManager::allocate(BufferManager::MODE mode, int width, int height, int format)
{
	m_Mode = mode;
	int n = 0;
	n = m_Mode == TRIPLE ? 3 : 4;
	for (int i = 0; i < n; i++)
	{
		/* allocate buffer */
		std::unique_ptr<VncGraphicBuffer> buffer(new VncGraphicBuffer(width, height, format));
		m_Buffers.push_back(std::move(buffer));
		if (!m_Buffers[i]->allocate())
		{
			return false;
		}
	}
	return true;
}

void BufferManager::release()
{
	m_Buffers.clear();
}

void BufferManager::getConsumer(VncGraphicBufferPtr &consumer, VncGraphicBufferPtr &compare)
{
	std::lock_guard<std::mutex> lock(m_Lock);
	if (m_Mode == WITH_COMPARE)
	{
		/* clean -> consumer, consumer -> compare, compare -> clean */
		int cleanIdx = m_Clean;
		int consumerIdx = m_Consumer;
		int compareIdx = m_Compare;
		m_Consumer = cleanIdx;
		m_Compare = consumerIdx;
		m_Clean = compareIdx;
		compare = m_Buffers[m_Compare].get();
	}
	else
	{
		/* clean -> consumer, consumer -> clean */
		int cleanIdx = m_Clean;
		int consumerIdx = m_Consumer;
		m_Consumer = cleanIdx;
		m_Clean = consumerIdx;
		compare = nullptr;
	}
	consumer = m_Buffers[m_Consumer].get();
}

VncGraphicBuffer *BufferManager::getProducer()
{
	std::lock_guard<std::mutex> lock(m_Lock);
	int cleanIdx = m_Clean;
	int producerIdx = m_Producer;
	m_Clean = producerIdx;
	m_Producer = cleanIdx;
	return m_Buffers[m_Producer].get();
}
