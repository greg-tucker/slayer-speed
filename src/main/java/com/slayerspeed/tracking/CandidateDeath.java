package com.slayerspeed.tracking;

final class CandidateDeath
{
	private final Object npcIdentity;
	private final int npcId;
	private final String npcName;
	private final int deathTick;
	private final boolean interacted;
	private boolean xpMatched;
	private boolean progressMatched;
	private boolean lootMatched;
	private boolean confirmed;
	private ProgressBatch progressBatch;
	private boolean batchEvidenceCounted;

	CandidateDeath(Object npcIdentity, int npcId, String npcName, int deathTick, boolean interacted)
	{
		this.npcIdentity = npcIdentity;
		this.npcId = npcId;
		this.npcName = npcName;
		this.deathTick = deathTick;
		this.interacted = interacted;
	}

	Object getNpcIdentity()
	{
		return npcIdentity;
	}

	int getNpcId()
	{
		return npcId;
	}

	String getNpcName()
	{
		return npcName;
	}

	int getDeathTick()
	{
		return deathTick;
	}

	boolean isInteracted()
	{
		return interacted;
	}

	boolean isXpMatched()
	{
		return xpMatched;
	}

	void setXpMatched()
	{
		xpMatched = true;
	}

	boolean isProgressMatched()
	{
		return progressMatched;
	}

	void setProgressMatched()
	{
		progressMatched = true;
	}

	void setLootMatched()
	{
		lootMatched = true;
	}

	boolean isConfirmed()
	{
		return confirmed;
	}

	boolean shouldConfirm()
	{
		return xpMatched || lootMatched || (progressMatched && interacted);
	}

	boolean hasProgressBatch()
	{
		return progressBatch != null;
	}

	void assignProgressBatch(ProgressBatch progressBatch)
	{
		this.progressBatch = progressBatch;
		progressMatched = true;
	}

	boolean recordBatchEvidence()
	{
		if (progressBatch == null || batchEvidenceCounted)
		{
			return false;
		}
		batchEvidenceCounted = true;
		return progressBatch.recordEvidence();
	}

	void confirm()
	{
		confirmed = true;
	}

	static final class ProgressBatch
	{
		private int reportedKills;
		private int evidencedKills;

		ProgressBatch(int reportedKills)
		{
			this.reportedKills = reportedKills;
		}

		boolean recordEvidence()
		{
			evidencedKills++;
			if (evidencedKills > reportedKills)
			{
				reportedKills++;
				return true;
			}
			return false;
		}
	}
}
