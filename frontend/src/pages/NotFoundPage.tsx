import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";

export default function NotFoundPage() {
  return (
    <EmptyState
      title="Page not found"
      description="That page doesn't exist or has moved."
      action={
        <Button asChild>
          <Link to="/dashboard">Go to dashboard</Link>
        </Button>
      }
    />
  );
}
