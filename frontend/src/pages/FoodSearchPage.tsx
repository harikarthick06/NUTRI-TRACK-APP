import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { toast } from "sonner";
import { Barcode, Globe, Plus, Search, Star } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState, ErrorState } from "@/components/ui/empty-state";
import { Input, NativeSelect } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { FoodRow, useDebounced } from "@/components/nutrition/FoodPicker";
import {
  useExternalSearch,
  useFoodCategories,
  useFoodSearch,
  useImportFood,
  useProviders,
  useToggleFavorite,
  type FoodScope,
} from "@/hooks/queries";
import { api, errorMessage } from "@/lib/api";
import type { FoodDetail } from "@/lib/types";
import { fmt } from "@/lib/utils";

const SCOPES: { value: FoodScope; label: string }[] = [
  { value: "ALL", label: "All foods" },
  { value: "RECENT", label: "Recent" },
  { value: "FAVORITES", label: "Favourites" },
  { value: "MINE", label: "My foods" },
];

function LocalSearch() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const [q, setQ] = useState(params.get("q") ?? "");
  const scope = (params.get("scope") as FoodScope) || "ALL";
  const category = params.get("category") ?? "";
  const [page, setPage] = useState(0);
  const debounced = useDebounced(q.trim());
  const search = useFoodSearch(debounced, scope, category, page);
  const categories = useFoodCategories();
  const fav = useToggleFavorite();

  useEffect(() => setPage(0), [debounced, scope, category]);
  useEffect(() => {
    const next = new URLSearchParams(params);
    if (debounced) next.set("q", debounced);
    else next.delete("q");
    if (next.toString() !== params.toString()) setParams(next, { replace: true });
  }, [debounced]); // eslint-disable-line react-hooks/exhaustive-deps

  const setParam = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value && value !== "ALL") next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
  };

  const data = search.data;
  return (
    <div className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-[1fr_auto_auto]">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input aria-label="Search foods" placeholder="Search by name, e.g. dosa, paneer, toor dal" value={q} onChange={(e) => setQ(e.target.value)} className="pl-9" />
        </div>
        <NativeSelect aria-label="Which foods" value={scope} onChange={(e) => setParam("scope", e.target.value)} className="sm:w-40">
          {SCOPES.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </NativeSelect>
        <NativeSelect aria-label="Category" value={category} onChange={(e) => setParam("category", e.target.value)} className="sm:w-52">
          <option value="">All categories</option>
          {categories.data?.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </NativeSelect>
      </div>

      {search.error ? (
        <ErrorState message={errorMessage(search.error)} onRetry={() => search.refetch()} />
      ) : search.isLoading ? (
        <div className="grid gap-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-16" />
          ))}
        </div>
      ) : !data || data.items.length === 0 ? (
        <EmptyState
          icon={Search}
          title="No foods found"
          description={scope === "FAVORITES" ? "Star foods to find them here quickly." : "Try a different name, check the online databases, or create your own food."}
          action={
            <Button asChild>
              <Link to="/foods/new">
                <Plus /> Create a food
              </Link>
            </Button>
          }
        />
      ) : (
        <Card>
          <CardContent className="p-0">
            <p className="border-b px-3 py-2 text-xs text-muted-foreground">{fmt(data.totalItems)} foods</p>
            <ul>
              {data.items.map((f) => (
                <FoodRow
                  key={f.id}
                  food={f}
                  onSelect={() => navigate(`/foods/${f.id}`)}
                  action={
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      className="mr-2"
                      aria-label={f.favorite ? `Remove ${f.name} from favourites` : `Add ${f.name} to favourites`}
                      aria-pressed={f.favorite}
                      onClick={() => fav.mutate({ id: f.id, favorite: !f.favorite }, { onError: (e) => toast.error(errorMessage(e)) })}
                    >
                      <Star className={f.favorite ? "fill-current text-carbs" : ""} />
                    </Button>
                  }
                />
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </Button>
          <span className="text-muted-foreground">
            Page {page + 1} of {data.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
            Next
          </Button>
        </div>
      )}
    </div>
  );
}

function OnlineSearch() {
  const providers = useProviders();
  const navigate = useNavigate();
  const enabled = providers.data?.filter((p) => p.enabled) ?? [];
  const [provider, setProvider] = useState("");
  const [q, setQ] = useState("");
  const debounced = useDebounced(q.trim(), 400);
  const active = provider || enabled[0]?.code || "";
  const results = useExternalSearch(active, debounced);
  const importFood = useImportFood();
  const [barcode, setBarcode] = useState("");
  const [lookingUp, setLookingUp] = useState(false);

  if (providers.isLoading) return <Skeleton className="h-32" />;

  const lookupBarcode = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^\d{8,14}$/.test(barcode.trim())) {
      toast.error("Enter the 8-14 digit number under the barcode");
      return;
    }
    setLookingUp(true);
    try {
      const food = await api<FoodDetail>(`/api/foods/barcode/${barcode.trim()}`);
      navigate(`/foods/${food.id}`);
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setLookingUp(false);
    }
  };

  return (
    <div className="grid gap-4">
      <Card>
        <CardContent className="grid gap-2 pt-5 text-sm">
          {providers.data?.map((p) => (
            <p key={p.code} className="flex items-center gap-2">
              <span className={`size-2 rounded-full ${p.enabled ? "bg-primary" : "bg-muted-foreground"}`} aria-hidden />
              <span className="font-medium">{p.name}</span>
              <span className="text-muted-foreground">{p.enabled ? "available" : p.reason ?? "turned off"}</span>
            </p>
          ))}
        </CardContent>
      </Card>

      <form onSubmit={lookupBarcode} className="flex gap-2">
        <Input aria-label="Barcode" inputMode="numeric" placeholder="Barcode number, e.g. 8901058000290" value={barcode} onChange={(e) => setBarcode(e.target.value)} />
        <Button type="submit" variant="outline" disabled={lookingUp}>
          <Barcode /> Look up
        </Button>
      </form>

      {enabled.length === 0 ? (
        <EmptyState
          icon={Globe}
          title="Online databases are turned off"
          description="An administrator can enable USDA FoodData Central (needs a free API key) or Open Food Facts in the server settings. The built-in Indian and USDA foods still work."
        />
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-[auto_1fr]">
            <NativeSelect aria-label="Database" value={active} onChange={(e) => setProvider(e.target.value)} className="sm:w-56">
              {enabled.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.name}
                </option>
              ))}
            </NativeSelect>
            <Input aria-label="Search online" placeholder="Search packaged and branded foods" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          {results.error && <ErrorState message={errorMessage(results.error)} onRetry={() => results.refetch()} />}
          {results.isFetching && <Skeleton className="h-24" />}
          {results.data && results.data.length === 0 && <p className="text-sm text-muted-foreground">No results.</p>}
          {results.data && results.data.length > 0 && (
            <Card>
              <CardContent className="p-0">
                <ul className="divide-y">
                  {results.data.map((r) => (
                    <li key={`${r.provider}-${r.externalId}`} className="flex items-center justify-between gap-3 p-3">
                      <div className="min-w-0">
                        <p className="truncate font-medium">{r.name}</p>
                        <p className="truncate text-xs text-muted-foreground">
                          {[r.brand, r.dataType, r.energyKcalPerBasis !== undefined ? `${fmt(r.energyKcalPerBasis)} kcal per 100 ${r.basisUnit === "ML" ? "mL" : "g"}` : null]
                            .filter(Boolean)
                            .join(" · ")}
                        </p>
                      </div>
                      <Button
                        size="sm"
                        disabled={importFood.isPending}
                        onClick={async () => {
                          try {
                            const f = await importFood.mutateAsync({ provider: r.provider, externalId: r.externalId });
                            toast.success("Imported");
                            navigate(`/foods/${f.id}`);
                          } catch (err) {
                            toast.error(errorMessage(err));
                          }
                        }}
                      >
                        Use this
                      </Button>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          )}
        </>
      )}
    </div>
  );
}

export default function FoodSearchPage() {
  return (
    <>
      <PageHeader
        title="Food search"
        description="Indian foods from IFCT 2017, USDA foods, built-in recipes and your own foods."
        actions={
          <Button asChild>
            <Link to="/foods/new">
              <Plus /> Create a food
            </Link>
          </Button>
        }
      />
      <Tabs defaultValue="local">
        <TabsList>
          <TabsTrigger value="local">NutriTrack foods</TabsTrigger>
          <TabsTrigger value="online">Online databases</TabsTrigger>
        </TabsList>
        <TabsContent value="local">
          <LocalSearch />
        </TabsContent>
        <TabsContent value="online">
          <OnlineSearch />
        </TabsContent>
      </Tabs>
    </>
  );
}
