import { useEffect, useState } from 'react';
import { Clock } from 'lucide-react';

interface RateLimitToastProps {
  retryAfter: number;
  onDismiss: () => void;
}

export function RateLimitToast({ retryAfter, onDismiss }: RateLimitToastProps) {
  const [remaining, setRemaining] = useState(retryAfter);

  useEffect(() => {
    if (remaining <= 0) {
      onDismiss();
      return;
    }

    const timer = setTimeout(() => setRemaining(remaining - 1), 1000);
    return () => clearTimeout(timer);
  }, [remaining, onDismiss]);

  return (
    <div className="flex items-start gap-3 p-4 bg-orange-50 border border-orange-200 rounded-lg">
      <Clock className="text-orange-500 flex-shrink-0 mt-0.5" size={20} />
      <div>
        <p className="font-semibold text-orange-900">Too Many Requests</p>
        <p className="text-sm text-orange-700 mt-1">
          Please wait <span className="font-mono font-bold">{remaining}s</span> before trying again.
        </p>
      </div>
    </div>
  );
}






