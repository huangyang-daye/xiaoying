import { EmptyState } from "@/components/ui/empty-state";

export default function NotFound() {
  return <div className="error-state"><h1 className="sr-only">页面不存在</h1><EmptyState title="这个页面还没有故事" description="页面可能已离开，回到首页寻找新的灵感吧。" action={{ href: "/templates", label: "返回首页" }} /></div>;
}
