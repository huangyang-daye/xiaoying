type PageHeadingProps = {
  eyebrow: string;
  title: string;
  description: string;
};

export function PageHeading({ eyebrow, title, description }: PageHeadingProps) {
  return (
    <section className="page-heading">
      <p className="eyebrow"><span aria-hidden="true" />{eyebrow}</p>
      <h1>{title}</h1>
      <p className="page-description">{description}</p>
    </section>
  );
}
